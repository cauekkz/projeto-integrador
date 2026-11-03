package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.CancellationRequestDTO;
import br.com.vanroute.backend.dtos.contract.CancellationResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.Contract;
import br.com.vanroute.backend.models.contract.ContractCancellation;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.CancellationStatus;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.ContractCancellationRepository;
import br.com.vanroute.backend.repositories.ContractRepository;
import br.com.vanroute.backend.repositories.DriverGroupRepository;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.RouteDriverRepository;
import br.com.vanroute.backend.repositories.StudentAbsenceRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import br.com.vanroute.backend.repositories.TripRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// cancelamento de contrato aceito (SCHEDULED ou ACTIVE) com aviso previo: qualquer parte pede com o motivo e o
// contrato e cancelado sozinho em NOTICE_DAYS (o worker faz isso no dia). Antes disso a outra parte pode aceitar
// (cancela na hora) e quem pediu pode desistir. Ninguem recusa: ninguem fica preso no contrato.
// Excecao: com parcela vencida ha OVERDUE_DAYS_TO_CANCEL_NOW dias ou mais, o pedido do motorista cancela na hora.
// Parcelas que vencem ate o cancelamento continuam devidas (inclusive as do aviso); multa de clausula do PDF fica
// fora do app. Cancelar tira o aluno das rotas e grupos do motorista e cancela as parcelas que ainda iam vencer;
// o resto (contrato, documento, assinaturas, parcelas, chat, viagens) fica no historico
@Service
public class ContractCancellationService {

    static final int NOTICE_DAYS = 30;
    static final int OVERDUE_DAYS_TO_CANCEL_NOW = 30;
    static final int REASON_MAX = 500;
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<ContractStatus> CANCELABLE = EnumSet.of(ContractStatus.SCHEDULED, ContractStatus.ACTIVE);
    private static final Set<TripStatus> ACTIVE_TRIP = EnumSet.of(TripStatus.STARTED, TripStatus.IN_PROGRESS, TripStatus.PAUSED);

    private final UserDriverContractRepository userDriverContractRepository;
    private final ContractRepository contractRepository;
    private final ContractCancellationRepository cancellationRepository;
    private final PaymentRepository paymentRepository;
    private final RouteDriverRepository routeDriverRepository;
    private final StudentStopRepository studentStopRepository;
    private final StudentAbsenceRepository studentAbsenceRepository;
    private final DriverGroupRepository driverGroupRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;

    public ContractCancellationService(UserDriverContractRepository userDriverContractRepository,
                                       ContractRepository contractRepository,
                                       ContractCancellationRepository cancellationRepository, PaymentRepository paymentRepository,
                                       RouteDriverRepository routeDriverRepository, StudentStopRepository studentStopRepository,
                                       StudentAbsenceRepository studentAbsenceRepository, DriverGroupRepository driverGroupRepository,
                                       TripRepository tripRepository, NotificationService notificationService) {
        this.userDriverContractRepository = userDriverContractRepository;
        this.contractRepository = contractRepository;
        this.cancellationRepository = cancellationRepository;
        this.paymentRepository = paymentRepository;
        this.routeDriverRepository = routeDriverRepository;
        this.studentStopRepository = studentStopRepository;
        this.studentAbsenceRepository = studentAbsenceRepository;
        this.driverGroupRepository = driverGroupRepository;
        this.tripRepository = tripRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public CancellationResponseDTO request(UUID contractId, UUID userId, CancellationRequestDTO body) {
        String reason = body == null || body.reason() == null ? "" : body.reason().trim();
        if (reason.isEmpty()) {
            throw new BadRequestException("Informe o motivo do cancelamento");
        }
        if (reason.length() > REASON_MAX) {
            throw new BadRequestException("O motivo pode ter no máximo " + REASON_MAX + " caracteres");
        }
        UserDriverContract udc = acceptedContractOf(contractId, userId);
        if (cancellationRepository.existsByContractIdAndStatus(contractId, CancellationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um pedido de cancelamento em andamento");
        }

        User requester = partyUser(udc, userId);
        LocalDateTime now = LocalDateTime.now(TripService.ZONE);
        LocalDate today = now.toLocalDate();
        ContractCancellation cancellation = new ContractCancellation();
        cancellation.setContract(udc.getContract());
        cancellation.setRequestedBy(requester);
        cancellation.setReason(reason);
        cancellation.setRequestedAt(now);

        boolean driver = udc.getDriver().getUserId().equals(userId);
        if (driver && paymentRepository.existsUnpaidDueUntil(contractId, today.minusDays(OVERDUE_DAYS_TO_CANCEL_NOW))) {
            cancel(udc);
            cancellation.setUnilateral(true);
            cancellation.decide(CancellationStatus.ACCEPTED, requester, now);
            cancellationRepository.save(cancellation);
            notifyOther(udc, userId, NotificationType.CONTRACT_CANCELED, "Contrato cancelado",
                    requester.getName() + " cancelou o contrato de " + udc.getStudent().getName()
                            + " por parcela em atraso há mais de " + OVERDUE_DAYS_TO_CANCEL_NOW + " dias. As parcelas vencidas continuam devidas. Motivo: " + reason);
        } else {
            cancellation.setStatus(CancellationStatus.PENDING);
            cancellation.setEffectiveDate(today.plusDays(NOTICE_DAYS));
            cancellationRepository.save(cancellation);
            notifyOther(udc, userId, NotificationType.CANCELLATION_REQUESTED, "Pedido de cancelamento",
                    requester.getName() + " pediu para cancelar o contrato de " + udc.getStudent().getName()
                            + ". Ele será cancelado em " + cancellation.getEffectiveDate().format(BR_DATE)
                            + " (aviso prévio de " + NOTICE_DAYS + " dias), ou antes, se você aceitar. Motivo: " + reason);
        }
        return CancellationResponseDTO.of(cancellation);
    }

    // a outra parte aceita antes do fim do aviso: cancela na hora
    @Transactional
    public CancellationResponseDTO accept(UUID contractId, UUID userId) {
        UserDriverContract udc = acceptedContractOf(contractId, userId);
        ContractCancellation cancellation = pending(contractId);
        if (cancellation.getRequestedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Quem pediu o cancelamento não pode aceitar o próprio pedido");
        }
        cancel(udc);
        cancellation.decide(CancellationStatus.ACCEPTED, partyUser(udc, userId), LocalDateTime.now(TripService.ZONE));
        notifyOther(udc, userId, NotificationType.CONTRACT_CANCELED, "Cancelamento aceito",
                partyUser(udc, userId).getName() + " aceitou cancelar o contrato de " + udc.getStudent().getName()
                        + ". O contrato foi cancelado agora");
        return CancellationResponseDTO.of(cancellation);
    }

    // quem pediu desiste antes do fim do aviso
    @Transactional
    public CancellationResponseDTO withdraw(UUID contractId, UUID userId) {
        UserDriverContract udc = acceptedContractOf(contractId, userId);
        ContractCancellation cancellation = pending(contractId);
        if (!cancellation.getRequestedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Só quem pediu o cancelamento pode desistir dele");
        }
        cancellation.decide(CancellationStatus.WITHDRAWN, partyUser(udc, userId), LocalDateTime.now(TripService.ZONE));
        notifyOther(udc, userId, NotificationType.CANCELLATION_WITHDRAWN, "Pedido de cancelamento retirado",
                partyUser(udc, userId).getName() + " desistiu de cancelar o contrato de " + udc.getStudent().getName()
                        + ". O contrato continua");
        return CancellationResponseDTO.of(cancellation);
    }

    // trava o contrato antes de tudo (pedido, aceite e desistencia ao mesmo tempo, ou o worker no fim do aviso,
    // passam um de cada vez) e confere que quem chama e parte e que o contrato ainda pode ser cancelado
    private UserDriverContract acceptedContractOf(UUID contractId, UUID userId) {
        contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        UserDriverContract udc = userDriverContractRepository.findDetailsByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        if (!udc.getDriver().getUserId().equals(userId) && !udc.getResponsible().getUserId().equals(userId)) {
            throw new AccessDeniedException("Usuário não faz parte deste contrato");
        }
        if (!CANCELABLE.contains(udc.getContract().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só um contrato aceito (agendado ou ativo) pode ser cancelado");
        }
        return udc;
    }

    private ContractCancellation pending(UUID contractId) {
        return cancellationRepository.findByContractIdAndStatus(contractId, CancellationStatus.PENDING)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum pedido de cancelamento em andamento"));
    }

    // cancela de fato: contrato, parcelas futuras e, se o aluno nao tiver outro contrato aceito com o motorista
    // (renovacao), rotas, faltas futuras e grupos. O worker faz a mesma coisa no fim do aviso (CancelAfterNotice)
    private void cancel(UserDriverContract udc) {
        LocalDate today = LocalDate.now(TripService.ZONE);
        UUID studentId = udc.getStudent().getId();
        UUID driverId = udc.getDriver().getUserId();
        Contract contract = udc.getContract();
        boolean keepInRoutes = userDriverContractRepository.existsOtherAccepted(driverId, studentId, contract.getId());
        List<UUID> routeIds = routeDriverRepository.findByDriverUserIdAndEndDateIsNull(driverId).stream()
                .map(rd -> rd.getRoute().getId())
                .toList();
        List<StudentStop> stops = keepInRoutes || routeIds.isEmpty() ? List.of()
                : studentStopRepository.findByStudentIdAndStopRouteIdIn(studentId, routeIds);
        // com a van rodando com o aluno, tirar ele da rota quebraria a viagem: espera terminar. Olha hoje e ontem: a
        // viagem que comecou 23:50 ainda roda depois da meia-noite (as esquecidas o worker fecha depois de 20 horas)
        boolean tripRunning = stops.stream().map(stop -> stop.getStop().getRoute().getId()).distinct()
                .anyMatch(routeId -> tripRepository.findFirstByRouteIdAndDateAndStatusIn(routeId, today, ACTIVE_TRIP).isPresent()
                        || tripRepository.findFirstByRouteIdAndDateAndStatusIn(routeId, today.minusDays(1), ACTIVE_TRIP).isPresent());
        if (tripRunning) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tem uma viagem rodando com " + udc.getStudent().getName() + " agora. Tente depois que ela terminar");
        }

        contract.setStatus(ContractStatus.CANCELED);
        // as que ainda iam vencer somem; as vencidas (e a de hoje) continuam devidas
        paymentRepository.findByContractIdAndStatusAndDueDateAfter(contract.getId(), PaymentStatus.PENDING, today)
                .forEach(payment -> payment.setStatus(PaymentStatus.CANCELED));
        if (keepInRoutes) {
            return;
        }
        studentStopRepository.deleteAll(stops);
        if (!routeIds.isEmpty()) {
            studentAbsenceRepository.deleteAll(
                    studentAbsenceRepository.findByStudentIdAndRouteIdInAndDateGreaterThanEqual(studentId, routeIds, today));
        }
        driverGroupRepository.findByDriverUserIdOrderByNameAsc(driverId)
                .forEach(group -> group.getStudents().removeIf(student -> student.getId().equals(studentId)));
    }

    private static User partyUser(UserDriverContract udc, UUID userId) {
        return udc.getDriver().getUserId().equals(userId) ? udc.getDriver().getUser() : udc.getResponsible().getUser();
    }

    private void notifyOther(UserDriverContract udc, UUID userId, NotificationType type, String title, String message) {
        UUID other = udc.getDriver().getUserId().equals(userId) ? udc.getResponsible().getUserId() : udc.getDriver().getUserId();
        notificationService.send(List.of(other), type, title, message, Map.of("contractId", udc.getContract().getId()));
    }
}
