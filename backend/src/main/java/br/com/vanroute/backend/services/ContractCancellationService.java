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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// cancelamento de contrato ativo: uma parte pede com o motivo e a outra aceita ou recusa; quem pediu pode desistir.
// Excecao: com 2+ parcelas atrasadas o pedido do motorista ja cancela, sem esperar o responsavel.
// Cancelar tira o aluno das rotas e dos grupos do motorista e cancela as parcelas que ainda iam vencer;
// as vencidas continuam devidas e o resto (contrato, documento, assinaturas, parcelas, chat, viagens) fica no historico
@Service
public class ContractCancellationService {

    // parcelas vencidas e nao pagas pro motorista poder cancelar sozinho
    static final int OVERDUE_TO_CANCEL_ALONE = 2;
    static final int REASON_MAX = 500;
    private static final Set<TripStatus> ACTIVE_TRIP = EnumSet.of(TripStatus.STARTED, TripStatus.IN_PROGRESS, TripStatus.PAUSED);

    private final UserDriverContractRepository userDriverContractRepository;
    private final ContractCancellationRepository cancellationRepository;
    private final PaymentRepository paymentRepository;
    private final RouteDriverRepository routeDriverRepository;
    private final StudentStopRepository studentStopRepository;
    private final StudentAbsenceRepository studentAbsenceRepository;
    private final DriverGroupRepository driverGroupRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;

    public ContractCancellationService(UserDriverContractRepository userDriverContractRepository,
                                       ContractCancellationRepository cancellationRepository, PaymentRepository paymentRepository,
                                       RouteDriverRepository routeDriverRepository, StudentStopRepository studentStopRepository,
                                       StudentAbsenceRepository studentAbsenceRepository, DriverGroupRepository driverGroupRepository,
                                       TripRepository tripRepository, NotificationService notificationService) {
        this.userDriverContractRepository = userDriverContractRepository;
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
        UserDriverContract udc = activeContractOf(contractId, userId);
        if (cancellationRepository.existsByContractIdAndStatus(contractId, CancellationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um pedido de cancelamento esperando resposta");
        }

        User requester = partyUser(udc, userId);
        LocalDateTime now = LocalDateTime.now(TripService.ZONE);
        ContractCancellation cancellation = new ContractCancellation();
        cancellation.setContract(udc.getContract());
        cancellation.setRequestedBy(requester);
        cancellation.setReason(reason);
        cancellation.setRequestedAt(now);
        cancellation.setStatus(CancellationStatus.PENDING);

        boolean driver = udc.getDriver().getUserId().equals(userId);
        if (driver && overdue(contractId) >= OVERDUE_TO_CANCEL_ALONE) {
            cancel(udc);
            cancellation.setUnilateral(true);
            cancellation.decide(CancellationStatus.ACCEPTED, requester, now);
            cancellationRepository.save(cancellation);
            notifyOther(udc, userId, NotificationType.CONTRACT_CANCELED, "Contrato cancelado",
                    requester.getName() + " cancelou o contrato de " + udc.getStudent().getName()
                            + " por parcelas em atraso. Motivo: " + reason);
        } else {
            cancellationRepository.save(cancellation);
            notifyOther(udc, userId, NotificationType.CANCELLATION_REQUESTED, "Pedido de cancelamento",
                    requester.getName() + " pediu para cancelar o contrato de " + udc.getStudent().getName() + ". Motivo: " + reason);
        }
        return CancellationResponseDTO.of(cancellation);
    }

    // a outra parte aceita: cancela na hora
    @Transactional
    public CancellationResponseDTO accept(UUID contractId, UUID userId) {
        UserDriverContract udc = activeContractOf(contractId, userId);
        ContractCancellation cancellation = pendingAnsweredBy(contractId, userId);
        cancel(udc);
        cancellation.decide(CancellationStatus.ACCEPTED, partyUser(udc, userId), LocalDateTime.now(TripService.ZONE));
        notifyOther(udc, userId, NotificationType.CONTRACT_CANCELED, "Cancelamento aceito",
                partyUser(udc, userId).getName() + " aceitou cancelar o contrato de " + udc.getStudent().getName());
        return CancellationResponseDTO.of(cancellation);
    }

    // a outra parte recusa: o contrato continua ativo
    @Transactional
    public CancellationResponseDTO reject(UUID contractId, UUID userId) {
        UserDriverContract udc = activeContractOf(contractId, userId);
        ContractCancellation cancellation = pendingAnsweredBy(contractId, userId);
        cancellation.decide(CancellationStatus.REJECTED, partyUser(udc, userId), LocalDateTime.now(TripService.ZONE));
        notifyOther(udc, userId, NotificationType.CANCELLATION_REJECTED, "Cancelamento recusado",
                partyUser(udc, userId).getName() + " recusou o cancelamento do contrato de " + udc.getStudent().getName()
                        + ". O contrato continua ativo");
        return CancellationResponseDTO.of(cancellation);
    }

    // quem pediu desiste
    @Transactional
    public CancellationResponseDTO withdraw(UUID contractId, UUID userId) {
        UserDriverContract udc = activeContractOf(contractId, userId);
        ContractCancellation cancellation = pending(contractId);
        if (!cancellation.getRequestedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Só quem pediu o cancelamento pode desistir dele");
        }
        cancellation.decide(CancellationStatus.WITHDRAWN, partyUser(udc, userId), LocalDateTime.now(TripService.ZONE));
        notifyOther(udc, userId, NotificationType.CANCELLATION_WITHDRAWN, "Pedido de cancelamento retirado",
                partyUser(udc, userId).getName() + " desistiu de cancelar o contrato de " + udc.getStudent().getName());
        return CancellationResponseDTO.of(cancellation);
    }

    private UserDriverContract activeContractOf(UUID contractId, UUID userId) {
        UserDriverContract udc = userDriverContractRepository.findDetailsByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        if (!udc.getDriver().getUserId().equals(userId) && !udc.getResponsible().getUserId().equals(userId)) {
            throw new AccessDeniedException("Usuário não faz parte deste contrato");
        }
        if (udc.getContract().getStatus() != ContractStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só um contrato ativo pode ser cancelado");
        }
        return udc;
    }

    private ContractCancellation pending(UUID contractId) {
        return cancellationRepository.findByContractIdAndStatus(contractId, CancellationStatus.PENDING)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum pedido de cancelamento esperando resposta"));
    }

    // aceitar e recusar e da outra parte, nao de quem pediu
    private ContractCancellation pendingAnsweredBy(UUID contractId, UUID userId) {
        ContractCancellation cancellation = pending(contractId);
        if (cancellation.getRequestedBy().getId().equals(userId)) {
            throw new AccessDeniedException("Quem pediu o cancelamento não pode responder o próprio pedido");
        }
        return cancellation;
    }

    private long overdue(UUID contractId) {
        return paymentRepository.countOverdue(contractId, LocalDate.now(TripService.ZONE));
    }

    // cancela de fato: contrato, parcelas futuras, rotas, faltas e grupos do motorista
    private void cancel(UserDriverContract udc) {
        LocalDate today = LocalDate.now(TripService.ZONE);
        UUID studentId = udc.getStudent().getId();
        List<UUID> routeIds = routeDriverRepository.findByDriverUserIdAndEndDateIsNull(udc.getDriver().getUserId()).stream()
                .map(rd -> rd.getRoute().getId())
                .toList();
        List<StudentStop> stops = routeIds.isEmpty() ? List.of() : studentStopRepository.findByStudentIdAndStopRouteIdIn(studentId, routeIds);
        // com a van rodando com o aluno, tirar ele da rota quebraria a viagem: espera terminar
        boolean tripRunning = stops.stream().map(stop -> stop.getStop().getRoute().getId()).distinct()
                .anyMatch(routeId -> tripRepository.findFirstByRouteIdAndDateAndStatusIn(routeId, today, ACTIVE_TRIP).isPresent());
        if (tripRunning) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tem uma viagem rodando com " + udc.getStudent().getName() + " agora. Tente depois que ela terminar");
        }

        Contract contract = udc.getContract();
        contract.setStatus(ContractStatus.CANCELED);
        // as que ainda iam vencer somem; as vencidas continuam devidas
        paymentRepository.findByContractIdAndStatusAndDueDateAfter(contract.getId(), PaymentStatus.PENDING, today)
                .forEach(payment -> payment.setStatus(PaymentStatus.CANCELED));
        studentStopRepository.deleteAll(stops);
        if (!routeIds.isEmpty()) {
            studentAbsenceRepository.deleteAll(
                    studentAbsenceRepository.findByStudentIdAndRouteIdInAndDateGreaterThanEqual(studentId, routeIds, today));
        }
        driverGroupRepository.findByDriverUserIdOrderByNameAsc(udc.getDriver().getUserId())
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
