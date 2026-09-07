package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.ContractProposalRequestDTO;
import br.com.vanroute.backend.dtos.contract.ContractRequestDTO;
import br.com.vanroute.backend.dtos.document.DocumentRequestDTO;
import br.com.vanroute.backend.models.contract.Contract;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.document.Document;
import br.com.vanroute.backend.models.document.enums.DocumentEntityType;
import br.com.vanroute.backend.models.document.enums.DocumentStatus;
import br.com.vanroute.backend.models.document.enums.DocumentType;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.ContractRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final DocumentRepository documentRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final DriverSchoolsRepository driverSchoolsRepository;
    private final EntityManager entityManager;

    public ContractService(
            ContractRepository contractRepository,
            DocumentRepository documentRepository,
            UserDriverContractRepository userDriverContractRepository,
            DriverSchoolsRepository driverSchoolsRepository,
            EntityManager entityManager) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.driverSchoolsRepository = driverSchoolsRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void createContract(UUID driverId, ContractProposalRequestDTO request) {
        //kaio jorge lixo
        boolean isDriverLinkedToSchool = driverSchoolsRepository
                .existsByDriverUserIdAndSchoolId(driverId, request.schoolId());
        
        if (!isDriverLinkedToSchool) {
            throw new RuntimeException("Motorista não possui essa escola");
        }

        // ja verificamo tudo I think
        Driver driverRef = entityManager.getReference(Driver.class, driverId);
        School schoolRef = entityManager.getReference(School.class, request.schoolId());
        Responsible responsibleRef = entityManager.getReference(Responsible.class, request.responsibleId());
        Student studentRef = entityManager.getReference(Student.class, request.studentId());
        User uploaderUserRef = entityManager.getReference(User.class, driverId);

        ContractRequestDTO contractDto = request.contract();
        DocumentRequestDTO documentDto = request.document();

        Contract contract = new Contract();
        contract.setPeriodicity(contractDto.periodicity());
        contract.setValue(contractDto.value());
        contract.setStartDate(contractDto.startDate());
        contract.setEndDate(contractDto.endDate());
        contract.setStatus(ContractStatus.PENDING); // UPPER MOON.
        
        contract = contractRepository.save(contract);

        
        Document document = new Document();
        document.setUrl(documentDto.url());
        document.setVersion(1);
        document.setEntityId(contract.getId());
        

        //IA falo pra bota
        try {
            document.setType(DocumentType.valueOf(documentDto.type()));
        } catch (IllegalArgumentException e) {
            document.setType(DocumentType.OTHER); 
        }
        
        document.setStatus(DocumentStatus.PENDING);
        document.setEntityType(DocumentEntityType.CONTRACT);
        document.setUploadedAt(LocalDateTime.now());
        document.setUploadedBy(uploaderUserRef);

        documentRepository.save(document);

        UserDriverContract userDriverContract = new UserDriverContract();
        userDriverContract.setResponsible(responsibleRef);
        userDriverContract.setStudent(studentRef);
        userDriverContract.setDriver(driverRef);
        userDriverContract.setSchool(schoolRef);
        userDriverContract.setContract(contract);

        userDriverContractRepository.save(userDriverContract);
    }
}
