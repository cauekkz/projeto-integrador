package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.ContractProposalRequestDTO;
import br.com.vanroute.backend.dtos.contract.ContractRequestDTO;
import br.com.vanroute.backend.dtos.document.DocumentRequestDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.Contract;
import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractPeriodicity;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.document.Document;
import br.com.vanroute.backend.models.document.enums.DocumentEntityType;
import br.com.vanroute.backend.models.document.enums.DocumentStatus;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.ContractCancellationRepository;
import br.com.vanroute.backend.repositories.ContractRepository;
import br.com.vanroute.backend.models.contract.ContractCancellation;
import br.com.vanroute.backend.models.contract.enums.CancellationStatus;
import br.com.vanroute.backend.repositories.SignatureRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ContractServiceTest {

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserDriverContractRepository userDriverContractRepository;
    @Mock
    private DriverSchoolsRepository driverSchoolsRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private StudentResponsibleService studentResponsibleService;
    @Mock
    private SignatureRepository signatureRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private ContractCancellationRepository contractCancellationRepository;
    @Mock
    private br.com.vanroute.backend.repositories.StudentResponsibleRepository studentResponsibleRepository;

    @InjectMocks
    private ContractService contractService;

    @Test
    void testCreateContract_DriverNotLinked_ThrowsException() {
        UUID driverId = UUID.randomUUID();
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, LocalDate.now(TripService.ZONE), LocalDate.now(TripService.ZONE).plusMonths(1)),
                new DocumentRequestDTO("uploads/" + driverId + "/x.pdf", "TRANSPORT_CONTRACT"));

        when(storageService.exists("uploads/" + driverId + "/x.pdf")).thenReturn(true);
        when(studentResponsibleService.isAdmin(request.responsibleId(), request.studentId())).thenReturn(true);
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driverId, request.schoolId())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> contractService.createContract(driverId, request));
    }

    @Test
    void testCreateContract_Success() {
        UUID driverId = UUID.randomUUID();
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, LocalDate.now(TripService.ZONE), LocalDate.now(TripService.ZONE).plusMonths(1)),
                new DocumentRequestDTO("uploads/" + driverId + "/x.pdf", "TRANSPORT_CONTRACT"));

        when(storageService.exists("uploads/" + driverId + "/x.pdf")).thenReturn(true);
        when(studentResponsibleService.isAdmin(request.responsibleId(), request.studentId())).thenReturn(true);
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driverId, request.schoolId())).thenReturn(true);
        when(entityManager.getReference(Driver.class, driverId)).thenReturn(new Driver());
        when(entityManager.getReference(School.class, request.schoolId())).thenReturn(new School());
        when(entityManager.getReference(Responsible.class, request.responsibleId())).thenReturn(new Responsible());
        when(entityManager.getReference(Student.class, request.studentId())).thenReturn(new Student());
        
        Contract mockedContract = new Contract();
        mockedContract.setId(UUID.randomUUID());
        when(contractRepository.save(any(Contract.class))).thenReturn(mockedContract);

        contractService.createContract(driverId, request);

        verify(contractRepository).save(any(Contract.class));
        verify(documentRepository).save(any());
        verify(userDriverContractRepository).save(any(UserDriverContract.class));
        // propor = o motorista assina os termos
        verify(signatureRepository).save(argThat(signature -> signature.getDocumentHash().length() == 64 && signature.getSignedAt() != null));
    }

    private static ContractProposalRequestDTO proposal(ContractRequestDTO terms) {
        return new ContractProposalRequestDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), terms,
                new DocumentRequestDTO("uploads/" + driverId + "/x.pdf", "TRANSPORT_CONTRACT"));
    }

    @Test
    void contractStartingInThePastIsRejected() {
        LocalDate yesterday = LocalDate.now(TripService.ZONE).minusDays(1);
        ContractProposalRequestDTO request = proposal(
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, yesterday, yesterday.plusMonths(3)));

        assertThrows(BadRequestException.class, () -> contractService.createContract(UUID.randomUUID(), request));
        verify(contractRepository, never()).save(any());
    }

    @Test
    void invalidContractTermsAreRejected() {
        LocalDate start = LocalDate.now(TripService.ZONE).plusDays(1);
        ContractRequestDTO[] invalid = {
                null,
                new ContractRequestDTO(null, BigDecimal.TEN, start, start.plusMonths(1)),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, null, start, start.plusMonths(1)),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.ZERO, start, start.plusMonths(1)),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, new BigDecimal("-10"), start, start.plusMonths(1)),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, new BigDecimal("10.005"), start, start.plusMonths(1)),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, start, start.minusDays(1)),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, start, start.plusYears(2).plusDays(1)),
        };
        for (ContractRequestDTO terms : invalid) {
            assertThrows(BadRequestException.class, () -> ContractService.validateTerms(terms));
        }
        // limites que valem: comeca hoje e dura exatamente 2 anos
        LocalDate today = LocalDate.now(TripService.ZONE);
        ContractService.validateTerms(new ContractRequestDTO(ContractPeriodicity.MONTHLY, new BigDecimal("350.50"), today, today.plusYears(2)));
    }

    @Test
    void monthlyInstallmentsKeepTheStartDayAfterAShortMonth() {
        List<LocalDate> dates = ContractService.installmentDates(ContractPeriodicity.MONTHLY,
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 4, 30));

        assertEquals(List.of(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30)), dates);
    }

    @Test
    void installmentsGoUntilTheEndDate() {
        assertEquals(5, ContractService.installmentDates(ContractPeriodicity.MONTHLY,
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 6, 30)).size());
        assertEquals(List.of(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 15)),
                ContractService.installmentDates(ContractPeriodicity.WEEKLY, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 20)));
        assertEquals(List.of(LocalDate.of(2027, 2, 1), LocalDate.of(2028, 2, 1)),
                ContractService.installmentDates(ContractPeriodicity.ANNUALLY, LocalDate.of(2027, 2, 1), LocalDate.of(2029, 1, 31)));
    }

    private UserDriverContract pendingContract(UUID responsibleId) {
        UserDriverContract udc = contract(responsibleId);
        when(userDriverContractRepository.findByContractId(udc.getContract().getId())).thenReturn(Optional.of(udc));
        return udc;
    }

    private static UserDriverContract contract(UUID responsibleId) {
        User user = new User();
        user.setId(responsibleId);
        user.setName("Maria");
        Responsible responsible = new Responsible();
        responsible.setUserId(responsibleId);
        responsible.setUser(user);
        Driver driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        User driverUser = new User();
        driverUser.setId(driver.getUserId());
        driverUser.setName("Joao Motorista");
        driver.setUser(driverUser);
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setName("Ana");
        School school = new School();
        school.setId(UUID.randomUUID());
        school.setName("Escola X");
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setStatus(ContractStatus.PENDING);
        contract.setPeriodicity(ContractPeriodicity.MONTHLY);
        contract.setValue(new BigDecimal("350.00"));
        LocalDate start = LocalDate.now(TripService.ZONE).plusDays(1);
        contract.setStartDate(start);
        contract.setEndDate(start.plusMonths(2).minusDays(1));
        UserDriverContract udc = new UserDriverContract();
        udc.setResponsible(responsible);
        udc.setDriver(driver);
        udc.setStudent(student);
        udc.setSchool(school);
        udc.setContract(contract);
        return udc;
    }

    @Test
    void acceptingSignsTheDocumentWithTheSameTermsTheDriverSigned() {
        UUID responsibleId = UUID.randomUUID();
        UserDriverContract udc = pendingContract(responsibleId);
        Document document = new Document();
        document.setUrl("contracts/x.pdf");
        document.setStatus(DocumentStatus.PENDING);
        when(documentRepository.findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, udc.getContract().getId()))
                .thenReturn(Optional.of(document));

        contractService.acceptContract(udc.getContract().getId(), responsibleId);

        assertEquals(DocumentStatus.SIGNED, document.getStatus());
        // o hash bate com o calculado na proposta (mesmos termos, mesmo valor mesmo com 350 x 350.00)
        String proposed = ContractService.termsHash(udc.getContract(), udc.getDriver().getUserId(), responsibleId,
                udc.getStudent().getId(), udc.getSchool().getId(), "contracts/x.pdf");
        verify(signatureRepository).save(argThat(signature -> signature.getUser().getId().equals(responsibleId)
                && signature.getDocumentHash().equals(proposed)));
    }

    @Test
    void acceptingGeneratesOneInstallmentPerPeriod() {
        UUID responsibleId = UUID.randomUUID();
        UserDriverContract udc = pendingContract(responsibleId);

        contractService.acceptContract(udc.getContract().getId(), responsibleId);

        assertEquals(ContractStatus.ACTIVE, udc.getContract().getStatus());
        LocalDate start = udc.getContract().getStartDate();
        verify(paymentRepository).saveAll(argThat((List<Payment> payments) -> payments.size() == 2
                && payments.get(0).getDueDate().equals(start) && payments.get(1).getDueDate().equals(start.plusMonths(1))
                && payments.stream().allMatch(p -> p.getStatus() == PaymentStatus.PENDING && p.getPaidAt() == null)));
    }

    @Test
    void proposalWhoseStartDatePassedCannotBeAccepted() {
        UUID responsibleId = UUID.randomUUID();
        UserDriverContract udc = pendingContract(responsibleId);
        udc.getContract().setStartDate(LocalDate.now(TripService.ZONE).minusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> contractService.acceptContract(udc.getContract().getId(), responsibleId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(ContractStatus.PENDING, udc.getContract().getStatus());
        verify(paymentRepository, never()).saveAll(any());
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void proposalDocumentMustBeTheTransportContract() {
        LocalDate start = LocalDate.now(TripService.ZONE).plusDays(1);
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, start, start.plusMonths(1)),
                new DocumentRequestDTO("contracts/x.pdf", "CNH"));

        assertThrows(BadRequestException.class, () -> contractService.createContract(UUID.randomUUID(), request));
        verify(contractRepository, never()).save(any());
    }

    @Test
    void proposalWithoutTheContractPdfIsRejected() {
        LocalDate start = LocalDate.now(TripService.ZONE).plusDays(1);
        ContractRequestDTO terms = new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, start, start.plusMonths(1));
        DocumentRequestDTO[] missing = { null, new DocumentRequestDTO(null, "TRANSPORT_CONTRACT"), new DocumentRequestDTO("  ", "TRANSPORT_CONTRACT") };
        for (DocumentRequestDTO document : missing) {
            ContractProposalRequestDTO request = new ContractProposalRequestDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), terms, document);
            assertThrows(BadRequestException.class, () -> contractService.createContract(UUID.randomUUID(), request));
        }

        UUID driverId = UUID.randomUUID();
        // PDF de outra pessoa (bastaria saber o fileKey dela)
        ContractProposalRequestDTO someoneElses = new ContractProposalRequestDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), terms,
                new DocumentRequestDTO("uploads/" + UUID.randomUUID() + "/contrato.pdf", "TRANSPORT_CONTRACT"));
        assertThrows(BadRequestException.class, () -> contractService.createContract(driverId, someoneElses));

        // fileKey da pasta dele, mas que nao existe no storage (upload que falhou)
        String missingKey = "uploads/" + driverId + "/nao-existe.pdf";
        when(storageService.exists(missingKey)).thenReturn(false);
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), terms,
                new DocumentRequestDTO(missingKey, "TRANSPORT_CONTRACT"));
        assertThrows(BadRequestException.class, () -> contractService.createContract(driverId, request));
        verify(contractRepository, never()).save(any());
    }

    @Test
    void driverWithdrawsAPendingProposal() {
        UserDriverContract udc = pendingContract(UUID.randomUUID());
        Document document = new Document();
        when(documentRepository.findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, udc.getContract().getId()))
                .thenReturn(Optional.of(document));

        contractService.withdrawContract(udc.getContract().getId(), udc.getDriver().getUserId());

        assertEquals(ContractStatus.CANCELED, udc.getContract().getStatus());
        assertEquals(DocumentStatus.REJECTED, document.getStatus());
    }

    @Test
    void onlyTheDriverWithdrawsAndOnlyWhilePending() {
        UserDriverContract udc = pendingContract(UUID.randomUUID());
        UUID contractId = udc.getContract().getId();

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> contractService.withdrawContract(contractId, udc.getResponsible().getUserId()));

        udc.getContract().setStatus(ContractStatus.ACTIVE);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> contractService.withdrawContract(contractId, udc.getDriver().getUserId()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(ContractStatus.ACTIVE, udc.getContract().getStatus());
    }

    @Test
    void changingTheTermsChangesTheHash() {
        UserDriverContract udc = contract(UUID.randomUUID());
        Contract contract = udc.getContract();
        String before = ContractService.termsHash(contract, udc.getDriver().getUserId(), udc.getResponsible().getUserId(),
                udc.getStudent().getId(), udc.getSchool().getId(), "x");
        contract.setValue(new BigDecimal("999"));

        assertNotEquals(before, ContractService.termsHash(contract, udc.getDriver().getUserId(), udc.getResponsible().getUserId(),
                udc.getStudent().getId(), udc.getSchool().getId(), "x"));
    }

    @Test
    void rejectingMarksTheDocumentRejected() {
        UUID responsibleId = UUID.randomUUID();
        UserDriverContract udc = pendingContract(responsibleId);
        Document document = new Document();
        when(documentRepository.findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, udc.getContract().getId()))
                .thenReturn(Optional.of(document));

        contractService.rejectContract(udc.getContract().getId(), responsibleId);

        assertEquals(DocumentStatus.REJECTED, document.getStatus());
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void testContractDetails_OnlyParties() {
        UUID contractId = UUID.randomUUID();
        Responsible responsible = new Responsible();
        responsible.setUserId(UUID.randomUUID());
        Driver driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        UserDriverContract udc = new UserDriverContract();
        udc.setResponsible(responsible);
        udc.setDriver(driver);
        when(userDriverContractRepository.findDetailsByContractId(contractId)).thenReturn(java.util.Optional.of(udc));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> contractService.getContractDetails(contractId, UUID.randomUUID()));
    }

    @SuppressWarnings("unchecked")
    private <T> jakarta.persistence.TypedQuery<T> query(Class<T> type, List<T> result) {
        jakarta.persistence.TypedQuery<T> q = org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        org.mockito.Mockito.lenient().when(q.setParameter(org.mockito.ArgumentMatchers.anyString(), any())).thenReturn(q);
        org.mockito.Mockito.lenient().when(q.setFirstResult(org.mockito.ArgumentMatchers.anyInt())).thenReturn(q);
        org.mockito.Mockito.lenient().when(q.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(q);
        org.mockito.Mockito.lenient().when(q.getResultList()).thenReturn(result);
        if (!result.isEmpty() && result.get(0) instanceof Long) {
            org.mockito.Mockito.lenient().when(q.getSingleResult()).thenReturn(result.get(0));
        }
        return q;
    }

    @Test
    void myContractsFiltersInTheQueryPaginatesAndShowsPendingCancellationAndOverdue() {
        UUID userId = UUID.randomUUID();
        UserDriverContract mine = contract(userId);
        UUID contractId = mine.getContract().getId();
        var page = query(UserDriverContract.class, List.of(mine));
        var count = query(Long.class, List.of(7L));
        org.mockito.ArgumentCaptor<String> jpql = org.mockito.ArgumentCaptor.forClass(String.class);
        when(entityManager.createQuery(jpql.capture(), org.mockito.ArgumentMatchers.eq(UserDriverContract.class))).thenReturn(page);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(count);
        ContractCancellation pending = new ContractCancellation();
        pending.setContract(mine.getContract());
        pending.setRequestedBy(mine.getResponsible().getUser());
        pending.setReason("mudanca");
        pending.setStatus(CancellationStatus.PENDING);
        when(contractCancellationRepository.findPendingOf(List.of(contractId))).thenReturn(List.of(pending));
        when(paymentRepository.countOverdueOf(org.mockito.ArgumentMatchers.eq(List.of(contractId)), any()))
                .thenReturn(List.<Object[]>of(new Object[]{contractId, 2L}));

        var result = contractService.findMyContracts(userId, new br.com.vanroute.backend.dtos.contract.MyContractsFilterRequestDTO(
                ContractStatus.ACTIVE, mine.getStudent().getId(), null), org.springframework.data.domain.PageRequest.of(1, 1));

        // so os filtros que vieram entram na consulta
        assertTrue(jpql.getValue().contains("c.status = :status") && jpql.getValue().contains("s.id = :studentId"));
        org.junit.jupiter.api.Assertions.assertFalse(jpql.getValue().contains(":driverId"));
        verify(page).setFirstResult(1);
        verify(page).setMaxResults(1);
        assertEquals(7L, result.getTotalElements());
        assertEquals(contractId, result.getContent().get(0).contractId());
        assertEquals("mudanca", result.getContent().get(0).pendingCancellation().reason());
        assertEquals(2L, result.getContent().get(0).overduePayments());
    }
}
