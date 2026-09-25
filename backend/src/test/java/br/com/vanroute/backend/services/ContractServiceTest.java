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
import br.com.vanroute.backend.repositories.ContractRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private ContractService contractService;

    @Test
    void testCreateContract_DriverNotLinked_ThrowsException() {
        UUID driverId = UUID.randomUUID();
        ContractProposalRequestDTO request = new ContractProposalRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ContractRequestDTO(ContractPeriodicity.MONTHLY, BigDecimal.TEN, LocalDate.now(), LocalDate.now().plusMonths(1)),
                new DocumentRequestDTO( "http://url", "OTHER"));

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
    }
}
