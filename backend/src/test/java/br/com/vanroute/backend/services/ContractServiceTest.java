package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.ContractProposalRequestDTO;
import br.com.vanroute.backend.dtos.contract.ContractRequestDTO;
import br.com.vanroute.backend.dtos.document.DocumentRequestDTO;
import br.com.vanroute.backend.models.contract.Contract;
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
import br.com.vanroute.backend.repositories.ContractRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @InjectMocks
    private ContractService contractService;

    @Test
    void testCreateContract_DriverNotLinked_ThrowsException() {
        UUID driverId = UUID.randomUUID();
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, LocalDate.now(), LocalDate.now().plusMonths(1)),
                new DocumentRequestDTO( "http://url", "OTHER"));

        when(studentResponsibleService.isAdmin(request.responsibleId(), request.studentId())).thenReturn(true);
        when(driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driverId, request.schoolId())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> contractService.createContract(driverId, request));
    }

    @Test
    void testCreateContract_Success() {
        UUID driverId = UUID.randomUUID();
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, LocalDate.now(), LocalDate.now().plusMonths(1)),
                new DocumentRequestDTO("http://test-url", "OTHER"));

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

    private UserDriverContract pendingContract(UUID responsibleId) {
        UserDriverContract udc = contract(responsibleId);
        when(userDriverContractRepository.findByContractId(udc.getContract().getId())).thenReturn(Optional.of(udc));
        return udc;
    }

    private static UserDriverContract contract(UUID responsibleId) {
        User user = new User();
        user.setId(responsibleId);
        Responsible responsible = new Responsible();
        responsible.setUserId(responsibleId);
        responsible.setUser(user);
        Driver driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        Student student = new Student();
        student.setId(UUID.randomUUID());
        School school = new School();
        school.setId(UUID.randomUUID());
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID());
        contract.setStatus(ContractStatus.PENDING);
        contract.setPeriodicity(ContractPeriodicity.MONTHLY);
        contract.setValue(new BigDecimal("350.00"));
        contract.setStartDate(LocalDate.of(2027, 2, 1));
        contract.setEndDate(LocalDate.of(2027, 3, 31));
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

    @Test
    void myContractsUsesTheStatusFilterWhenGiven() {
        UUID userId = UUID.randomUUID();

        contractService.findMyContracts(userId, br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE);
        verify(userDriverContractRepository).findAllOfUserByStatus(userId, br.com.vanroute.backend.models.contract.enums.ContractStatus.ACTIVE);

        contractService.findMyContracts(userId, null);
        verify(userDriverContractRepository).findAllOfUser(userId);
    }
}
