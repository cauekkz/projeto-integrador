package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.CancellationRequestDTO;
import br.com.vanroute.backend.dtos.contract.CancellationResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.Contract;
import br.com.vanroute.backend.models.contract.ContractCancellation;
import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.CancellationStatus;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import br.com.vanroute.backend.models.group.DriverGroup;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteDriver;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractCancellationServiceTest {

    @Mock
    private UserDriverContractRepository userDriverContractRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractCancellationRepository cancellationRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RouteDriverRepository routeDriverRepository;
    @Mock
    private StudentStopRepository studentStopRepository;
    @Mock
    private StudentAbsenceRepository studentAbsenceRepository;
    @Mock
    private DriverGroupRepository driverGroupRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ContractCancellationService service;

    private UserDriverContract udc;
    private UUID contractId;
    private UUID driverId;
    private UUID responsibleId;
    private Student student;
    private Route route;
    private StudentStop stop;
    private DriverGroup group;
    private Payment futurePayment;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        responsibleId = UUID.randomUUID();
        Driver driver = new Driver();
        driver.setUserId(driverId);
        driver.setUser(user(driverId, "Joao Motorista"));
        Responsible responsible = new Responsible();
        responsible.setUserId(responsibleId);
        responsible.setUser(user(responsibleId, "Maria"));
        student = new Student();
        student.setId(UUID.randomUUID());
        student.setName("Ana");
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setValue(new BigDecimal("350.00"));
        contractId = contract.getId();
        udc = new UserDriverContract();
        udc.setDriver(driver);
        udc.setResponsible(responsible);
        udc.setStudent(student);
        udc.setContract(contract);
        when(userDriverContractRepository.findDetailsByContractId(contractId)).thenReturn(Optional.of(udc));
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        // o aluno esta numa rota do motorista e num grupo dele
        route = new Route();
        route.setId(UUID.randomUUID());
        RouteDriver routeDriver = new RouteDriver();
        routeDriver.setRoute(route);
        routeDriver.setDriver(driver);
        when(routeDriverRepository.findByDriverUserIdAndEndDateIsNull(driverId)).thenReturn(List.of(routeDriver));
        RouteStop routeStop = new RouteStop();
        routeStop.setRoute(route);
        stop = new StudentStop();
        stop.setStudent(student);
        stop.setStop(routeStop);
        when(studentStopRepository.findByStudentIdAndStopRouteIdIn(student.getId(), List.of(route.getId()))).thenReturn(List.of(stop));
        group = new DriverGroup();
        group.getStudents().add(student);
        when(driverGroupRepository.findByDriverUserIdOrderByNameAsc(driverId)).thenReturn(List.of(group));
        futurePayment = new Payment();
        futurePayment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByContractIdAndStatusAndDueDateAfter(eq(contractId), eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(futurePayment));
    }

    private static User user(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    // tem parcela vencida ha 30 dias ou mais
    private void oldOverdue(boolean exists) {
        when(paymentRepository.existsUnpaidDueUntil(eq(contractId), any())).thenReturn(exists);
    }

    private ContractCancellation pendingFrom(UUID requesterId) {
        ContractCancellation pending = new ContractCancellation();
        pending.setContract(udc.getContract());
        pending.setRequestedBy(requesterId.equals(driverId) ? udc.getDriver().getUser() : udc.getResponsible().getUser());
        pending.setReason("mudanca de cidade");
        pending.setStatus(CancellationStatus.PENDING);
        when(cancellationRepository.findByContractIdAndStatus(contractId, CancellationStatus.PENDING)).thenReturn(Optional.of(pending));
        return pending;
    }

    private void assertNotCanceled() {
        assertEquals(ContractStatus.ACTIVE, udc.getContract().getStatus());
        assertEquals(PaymentStatus.PENDING, futurePayment.getStatus());
        verify(studentStopRepository, never()).deleteAll(any());
        assertTrue(group.getStudents().contains(student));
    }

    private void assertCanceled() {
        assertEquals(ContractStatus.CANCELED, udc.getContract().getStatus());
        // a parcela que ainda ia vencer some; as vencidas continuam (a consulta so traz as futuras)
        assertEquals(PaymentStatus.CANCELED, futurePayment.getStatus());
        verify(studentStopRepository).deleteAll(List.of(stop));
        verify(studentAbsenceRepository).findByStudentIdAndRouteIdInAndDateGreaterThanEqual(eq(student.getId()), eq(List.of(route.getId())), any());
        assertFalse(group.getStudents().contains(student));
    }

    @Test
    void responsibleAsksAndTheContractEndsAfterTheNotice() {
        oldOverdue(false);

        CancellationResponseDTO result = service.request(contractId, responsibleId, new CancellationRequestDTO("  mudanca de cidade  "));

        assertEquals(CancellationStatus.PENDING, result.status());
        assertEquals("mudanca de cidade", result.reason());
        assertEquals(LocalDate.now(TripService.ZONE).plusDays(ContractCancellationService.NOTICE_DAYS), result.effectiveDate());
        assertFalse(result.unilateral());
        assertNotCanceled();
        verify(notificationService).send(eq(List.of(driverId)), eq(NotificationType.CANCELLATION_REQUESTED), anyString(), anyString(), anyMap());
    }

    @Test
    void responsibleWithDebtCanAskButTheDebtStays() {
        // o pedido do responsavel nao olha as parcelas: a divida continua (assertNotCanceled: nada muda agora)
        oldOverdue(true);

        CancellationResponseDTO result = service.request(contractId, responsibleId, new CancellationRequestDTO("vou mudar"));

        assertEquals(CancellationStatus.PENDING, result.status());
        assertNotCanceled();
    }

    @Test
    void driverWithoutOldOverdueAlsoGivesNotice() {
        oldOverdue(false);

        CancellationResponseDTO result = service.request(contractId, driverId, new CancellationRequestDTO("nao paga"));

        assertEquals(CancellationStatus.PENDING, result.status());
        assertNotCanceled();
        verify(notificationService).send(eq(List.of(responsibleId)), eq(NotificationType.CANCELLATION_REQUESTED), anyString(), anyString(), anyMap());
    }

    @Test
    void driverWithPaymentOverdueFor30DaysCancelsRightAway() {
        oldOverdue(true);

        CancellationResponseDTO result = service.request(contractId, driverId, new CancellationRequestDTO("2 meses sem pagar"));

        assertEquals(CancellationStatus.ACCEPTED, result.status());
        assertTrue(result.unilateral());
        assertEquals(driverId, result.decidedById());
        assertCanceled();
        verify(paymentRepository).existsUnpaidDueUntil(contractId,
                LocalDate.now(TripService.ZONE).minusDays(ContractCancellationService.OVERDUE_DAYS_TO_CANCEL_NOW));
        verify(notificationService).send(eq(List.of(responsibleId)), eq(NotificationType.CONTRACT_CANCELED), anyString(), anyString(), anyMap());
    }

    @Test
    void scheduledContractCanBeCanceledToo() {
        udc.getContract().setStatus(ContractStatus.SCHEDULED);
        pendingFrom(responsibleId);

        service.accept(contractId, driverId);

        assertEquals(ContractStatus.CANCELED, udc.getContract().getStatus());
    }

    @Test
    void renewedStudentStaysInTheRoutes() {
        pendingFrom(responsibleId);
        when(userDriverContractRepository.existsOtherAccepted(driverId, student.getId(), contractId)).thenReturn(true);

        service.accept(contractId, driverId);

        assertEquals(ContractStatus.CANCELED, udc.getContract().getStatus());
        assertEquals(PaymentStatus.CANCELED, futurePayment.getStatus());
        verify(studentStopRepository, never()).deleteAll(any());
        assertTrue(group.getStudents().contains(student));
    }

    @Test
    void requestNeedsAReasonAnActiveContractAndNoOtherPendingRequest() {
        assertThrows(BadRequestException.class, () -> service.request(contractId, responsibleId, null));
        assertThrows(BadRequestException.class, () -> service.request(contractId, responsibleId, new CancellationRequestDTO("   ")));
        assertThrows(BadRequestException.class, () -> service.request(contractId, responsibleId, new CancellationRequestDTO("x".repeat(501))));
        assertThrows(AccessDeniedException.class, () -> service.request(contractId, UUID.randomUUID(), new CancellationRequestDTO("x")));

        when(cancellationRepository.existsByContractIdAndStatus(contractId, CancellationStatus.PENDING)).thenReturn(true);
        ResponseStatusException duplicated = assertThrows(ResponseStatusException.class,
                () -> service.request(contractId, responsibleId, new CancellationRequestDTO("x")));
        assertEquals(HttpStatus.CONFLICT, duplicated.getStatusCode());

        udc.getContract().setStatus(ContractStatus.PENDING);
        ResponseStatusException notActive = assertThrows(ResponseStatusException.class,
                () -> service.request(contractId, responsibleId, new CancellationRequestDTO("x")));
        assertEquals(HttpStatus.CONFLICT, notActive.getStatusCode());
        verify(cancellationRepository, never()).save(any());
    }

    @Test
    void theOtherPartyAcceptsAndTheContractIsCanceledRightAway() {
        pendingFrom(responsibleId);

        CancellationResponseDTO result = service.accept(contractId, driverId);

        assertEquals(CancellationStatus.ACCEPTED, result.status());
        assertEquals(driverId, result.decidedById());
        assertCanceled();
        verify(notificationService).send(eq(List.of(responsibleId)), eq(NotificationType.CONTRACT_CANCELED), anyString(), anyString(), anyMap());
    }

    @Test
    void whoAskedCannotAnswerTheirOwnRequest() {
        pendingFrom(responsibleId);

        assertThrows(AccessDeniedException.class, () -> service.accept(contractId, responsibleId));
        assertNotCanceled();
    }

    @Test
    void onlyWhoAskedWithdraws() {
        pendingFrom(driverId);
        assertThrows(AccessDeniedException.class, () -> service.withdraw(contractId, responsibleId));

        CancellationResponseDTO result = service.withdraw(contractId, driverId);

        assertEquals(CancellationStatus.WITHDRAWN, result.status());
        assertNotCanceled();
        verify(notificationService).send(eq(List.of(responsibleId)), eq(NotificationType.CANCELLATION_WITHDRAWN), anyString(), anyString(), anyMap());
    }

    @Test
    void answeringWithoutAPendingRequestIsNotFound() {
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> service.accept(contractId, driverId));
    }

    @Test
    void cannotCancelWhileTheVanIsRunningWithTheStudent() {
        pendingFrom(responsibleId);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusIn(eq(route.getId()), any(), anyCollection()))
                .thenReturn(Optional.of(new Trip()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.accept(contractId, driverId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertNotCanceled();
    }
}
