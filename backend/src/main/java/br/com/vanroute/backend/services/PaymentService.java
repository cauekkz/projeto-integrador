package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.payment.MarkPaidRequestDTO;
import br.com.vanroute.backend.dtos.payment.PaymentResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// parcelas dos contratos. Sem gateway: o responsavel paga no Pix do motorista e o motorista confirma aqui.
// Atrasada (OVERDUE) e calculada na leitura: PENDING com vencimento antes de hoje
@Service
public class PaymentService {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FROM = "FROM Payment p JOIN p.contract c JOIN UserDriverContract u ON u.contract = c "
            + "JOIN u.student s JOIN u.driver d JOIN d.user du JOIN u.responsible r JOIN r.user ru "
            + "WHERE (d.userId = :me OR r.userId = :me)";

    private final EntityManager entityManager;
    private final PaymentRepository paymentRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final NotificationService notificationService;

    public PaymentService(EntityManager entityManager, PaymentRepository paymentRepository,
                          UserDriverContractRepository userDriverContractRepository, NotificationService notificationService) {
        this.entityManager = entityManager;
        this.paymentRepository = paymentRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.notificationService = notificationService;
    }

    public record Filter(UUID studentId, UUID driverId, UUID responsibleId, UUID contractId, PaymentStatus status,
                         LocalDate from, LocalDate to) {
    }

    // parcelas dos contratos do usuario (motorista ou responsavel), por vencimento; todos os filtros sao opcionais
    @Transactional(readOnly = true)
    public Page<PaymentResponseDTO> list(UUID userId, Filter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new BadRequestException("O fim do período não pode ser antes do início");
        }
        LocalDate today = LocalDate.now(TripService.ZONE);
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        params.put("me", userId);
        condition(where, params, "s.id = :studentId", "studentId", filter.studentId());
        condition(where, params, "d.userId = :driverId", "driverId", filter.driverId());
        condition(where, params, "r.userId = :responsibleId", "responsibleId", filter.responsibleId());
        condition(where, params, "c.id = :contractId", "contractId", filter.contractId());
        condition(where, params, "p.dueDate >= :from", "from", filter.from());
        condition(where, params, "p.dueDate <= :to", "to", filter.to());
        if (filter.status() != null) {
            switch (filter.status()) {
                case PENDING -> condition(where, params, "p.status = :pending AND p.dueDate >= :today", "today", today);
                case OVERDUE -> condition(where, params, "(p.status = :overdue OR (p.status = :pending AND p.dueDate < :today))", "today", today);
                default -> condition(where, params, "p.status = :status", "status", filter.status());
            }
            if (filter.status() == PaymentStatus.PENDING || filter.status() == PaymentStatus.OVERDUE) {
                params.put("pending", PaymentStatus.PENDING);
            }
            if (filter.status() == PaymentStatus.OVERDUE) {
                params.put("overdue", PaymentStatus.OVERDUE);
            }
        }

        TypedQuery<PaymentResponseDTO> query = entityManager.createQuery(
                "SELECT new br.com.vanroute.backend.dtos.payment.PaymentResponseDTO(p.id, c.id, s.id, s.name, d.userId, du.name, "
                        + "r.userId, ru.name, p.value, p.dueDate, p.status, p.paymentMethod, p.paidAt, d.pixKeyType, d.pixKey) "
                        + FROM + where + " ORDER BY p.dueDate, s.name, p.id", PaymentResponseDTO.class);
        TypedQuery<Long> count = entityManager.createQuery("SELECT COUNT(p) " + FROM + where, Long.class);
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            count.setParameter(name, value);
        });
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<PaymentResponseDTO> content = query.getResultList().stream().map(p -> withDisplayStatus(p, today)).toList();
        return new PageImpl<>(content, pageable, count.getSingleResult());
    }

    // o motorista confirma que recebeu; nao tem volta
    @Transactional
    public PaymentResponseDTO markPaid(UUID paymentId, UUID driverId, MarkPaidRequestDTO request) {
        if (request == null || request.method() == null) {
            throw new BadRequestException("Informe como a parcela foi paga (PIX, CASH, BOLETO ou CREDIT_CARD)");
        }
        Payment payment = pendingOfDriver(paymentId, driverId);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentMethod(request.method());
        payment.setPaidAt(LocalDateTime.now(TripService.ZONE));
        UserDriverContract udc = contractOf(payment);
        refreshFinancialStatus(udc);
        notificationService.send(List.of(udc.getResponsible().getUserId()), NotificationType.PAYMENT, "Pagamento confirmado",
                udc.getDriver().getUser().getName() + " confirmou o pagamento da parcela de "
                        + payment.getDueDate().format(BR_DATE) + " de " + udc.getStudent().getName(),
                Map.of("paymentId", payment.getId(), "contractId", udc.getContract().getId()));
        return toResponse(payment, udc);
    }

    // o motorista dispensa uma parcela (ex.: o contrato comecou no meio do mes); nao tem volta
    @Transactional
    public PaymentResponseDTO cancel(UUID paymentId, UUID driverId) {
        Payment payment = pendingOfDriver(paymentId, driverId);
        payment.setStatus(PaymentStatus.CANCELED);
        UserDriverContract udc = contractOf(payment);
        refreshFinancialStatus(udc);
        notificationService.send(List.of(udc.getResponsible().getUserId()), NotificationType.PAYMENT, "Parcela cancelada",
                udc.getDriver().getUser().getName() + " cancelou a parcela de " + payment.getDueDate().format(BR_DATE)
                        + " de " + udc.getStudent().getName() + ". Ela não precisa mais ser paga",
                Map.of("paymentId", payment.getId(), "contractId", udc.getContract().getId()));
        return toResponse(payment, udc);
    }

    private Payment pendingOfDriver(UUID paymentId, UUID driverId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Parcela não encontrada"));
        UserDriverContract udc = contractOf(payment);
        if (!udc.getDriver().getUserId().equals(driverId)) {
            throw new AccessDeniedException("Só o motorista do contrato mexe nas parcelas");
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.OVERDUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa parcela já foi paga ou cancelada");
        }
        return payment;
    }

    // quitou ou dispensou a parcela: o responsavel volta a ficar em dia na hora, sem esperar o worker
    private void refreshFinancialStatus(UserDriverContract udc) {
        paymentRepository.flush();
        long overdue = paymentRepository.countOverdueOfResponsible(udc.getResponsible().getUserId(), LocalDate.now(TripService.ZONE));
        udc.getResponsible().setFinancialStatus(overdue > 0 ? FinancialStatus.OVERDUE : FinancialStatus.UP_TO_DATE);
    }

    private UserDriverContract contractOf(Payment payment) {
        return userDriverContractRepository.findDetailsByContractId(payment.getContract().getId())
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
    }

    private static void condition(StringBuilder where, Map<String, Object> params, String clause, String name, Object value) {
        if (value != null) {
            where.append(" AND ").append(clause);
            params.put(name, value);
        }
    }

    private static PaymentResponseDTO withDisplayStatus(PaymentResponseDTO p, LocalDate today) {
        if (p.status() != PaymentStatus.PENDING || !p.dueDate().isBefore(today)) {
            return p;
        }
        return new PaymentResponseDTO(p.id(), p.contractId(), p.studentId(), p.studentName(), p.driverId(), p.driverName(),
                p.responsibleId(), p.responsibleName(), p.value(), p.dueDate(), PaymentStatus.OVERDUE, p.paymentMethod(),
                p.paidAt(), p.driverPixKeyType(), p.driverPixKey());
    }

    private static PaymentResponseDTO toResponse(Payment payment, UserDriverContract udc) {
        return withDisplayStatus(new PaymentResponseDTO(payment.getId(), udc.getContract().getId(), udc.getStudent().getId(),
                udc.getStudent().getName(), udc.getDriver().getUserId(), udc.getDriver().getUser().getName(),
                udc.getResponsible().getUserId(), udc.getResponsible().getUser().getName(), payment.getValue(),
                payment.getDueDate(), payment.getStatus(), payment.getPaymentMethod(), payment.getPaidAt(),
                udc.getDriver().getPixKeyType(), udc.getDriver().getPixKey()), LocalDate.now(TripService.ZONE));
    }
}
