package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.payment.MarkPaidRequestDTO;
import br.com.vanroute.backend.dtos.payment.PaymentResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.Contract;
import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.PaymentMethod;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.models.user.enums.PixKeyType;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserDriverContractRepository userDriverContractRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService service;

    private UUID driverId;
    private UUID responsibleId;
    private Payment payment;
    private Responsible responsible;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        responsibleId = UUID.randomUUID();
        Driver driver = new Driver();
        driver.setUserId(driverId);
        User driverUser = new User();
        driverUser.setName("Joao Motorista");
        driver.setUser(driverUser);
        driver.setPix(PixKeyType.EMAIL, "joao@van.com");
        responsible = new Responsible();
        responsible.setUserId(responsibleId);
        responsible.setUser(new User());
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setName("Ana");
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        UserDriverContract udc = new UserDriverContract();
        udc.setDriver(driver);
        udc.setResponsible(responsible);
        udc.setStudent(student);
        udc.setContract(contract);
        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setContract(contract);
        payment.setValue(new BigDecimal("350.00"));
        payment.setStatus(PaymentStatus.PENDING);
        // vencida: aparece como OVERDUE enquanto nao for paga
        payment.setDueDate(LocalDate.now(TripService.ZONE).minusDays(3));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(userDriverContractRepository.findDetailsByContractId(contract.getId())).thenReturn(Optional.of(udc));
    }

    @Test
    void driverConfirmsAnOverduePaymentAndTheResponsibleIsNotified() {
        PaymentResponseDTO result = service.markPaid(payment.getId(), driverId, new MarkPaidRequestDTO(PaymentMethod.PIX));

        assertEquals(PaymentStatus.PAID, result.status());
        assertEquals(PaymentMethod.PIX, result.paymentMethod());
        assertNotNull(payment.getPaidAt());
        assertEquals("joao@van.com", result.driverPixKey());
        verify(notificationService).send(eq(List.of(responsibleId)), eq(NotificationType.PAYMENT), eq("Pagamento confirmado"), anyString(), anyMap());
    }

    @Test
    void driverCancelsAPayment() {
        PaymentResponseDTO result = service.cancel(payment.getId(), driverId);

        assertEquals(PaymentStatus.CANCELED, result.status());
        assertNull(payment.getPaidAt());
        verify(notificationService).send(eq(List.of(responsibleId)), eq(NotificationType.PAYMENT), eq("Parcela cancelada"), anyString(), anyMap());
    }

    @Test
    void onlyTheDriverConfirmsAndNeedsTheMethod() {
        assertThrows(AccessDeniedException.class,
                () -> service.markPaid(payment.getId(), responsibleId, new MarkPaidRequestDTO(PaymentMethod.PIX)));
        assertThrows(AccessDeniedException.class, () -> service.cancel(payment.getId(), responsibleId));
        assertThrows(BadRequestException.class, () -> service.markPaid(payment.getId(), driverId, new MarkPaidRequestDTO(null)));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(notificationService, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyMap());
    }

    @Test
    void confirmingTheLastOverduePutsTheResponsibleBackUpToDate() {
        when(paymentRepository.countOverdueOfResponsible(org.mockito.ArgumentMatchers.eq(responsibleId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        service.markPaid(payment.getId(), driverId, new MarkPaidRequestDTO(PaymentMethod.PIX));

        assertEquals(FinancialStatus.UP_TO_DATE, responsible.getFinancialStatus());
    }

    @Test
    void stillOverdueIfAnotherPaymentIsLate() {
        when(paymentRepository.countOverdueOfResponsible(org.mockito.ArgumentMatchers.eq(responsibleId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);

        service.cancel(payment.getId(), driverId);

        assertEquals(FinancialStatus.OVERDUE, responsible.getFinancialStatus());
    }

    @Test
    void anOverduePaymentMarkedByTheWorkerCanBeConfirmed() {
        payment.setStatus(PaymentStatus.OVERDUE);

        PaymentResponseDTO result = service.markPaid(payment.getId(), driverId, new MarkPaidRequestDTO(PaymentMethod.CASH));

        assertEquals(PaymentStatus.PAID, result.status());
    }

    @Test
    void paidOrCanceledHasNoWayBack() {
        service.markPaid(payment.getId(), driverId, new MarkPaidRequestDTO(PaymentMethod.CASH));

        ResponseStatusException again = assertThrows(ResponseStatusException.class,
                () -> service.markPaid(payment.getId(), driverId, new MarkPaidRequestDTO(PaymentMethod.PIX)));
        ResponseStatusException cancel = assertThrows(ResponseStatusException.class, () -> service.cancel(payment.getId(), driverId));

        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, cancel.getStatusCode());
        assertEquals(PaymentMethod.CASH, payment.getPaymentMethod());
    }
}
