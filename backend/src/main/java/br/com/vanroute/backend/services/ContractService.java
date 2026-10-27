package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.ContractSummaryResponseDTO;
import br.com.vanroute.backend.models.address.Address;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;

import br.com.vanroute.backend.dtos.contract.ContractDetailsResponseDTO;

import br.com.vanroute.backend.exceptions.BadRequestException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
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

import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import java.util.List;
import java.util.Optional;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final DocumentRepository documentRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final DriverSchoolsRepository driverSchoolsRepository;
    private final PaymentRepository paymentRepository;
    private final StudentResponsibleService studentResponsibleService;
    private final EntityManager entityManager;

    public ContractService(
            ContractRepository contractRepository,
            DocumentRepository documentRepository,
            UserDriverContractRepository userDriverContractRepository,
            DriverSchoolsRepository driverSchoolsRepository,
            PaymentRepository paymentRepository,
            StudentResponsibleService studentResponsibleService,
            EntityManager entityManager) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.driverSchoolsRepository = driverSchoolsRepository;
        this.paymentRepository = paymentRepository;
        this.studentResponsibleService = studentResponsibleService;
        this.entityManager = entityManager;
    }

    @Transactional
    public UserDriverContract createContract(UUID driverId, ContractProposalRequestDTO request) {
        if (!studentResponsibleService.isAdmin(request.responsibleId(), request.studentId())) {
            throw new BadRequestException("O Responsável não possui conexão com a criança informada.");
        }

        //kaio jorge lixo
        boolean isDriverLinkedToSchool = driverSchoolsRepository
                .existsByDriverUserIdAndSchoolId(driverId, request.schoolId());
        
        if (!isDriverLinkedToSchool) {
            throw new AccessDeniedException("Motorista não possui essa escola");
        }

        boolean hasActiveOrPending = userDriverContractRepository
                .existsByDriverUserIdAndStudentIdAndContractStatusIn(driverId, request.studentId(), 
                List.of(ContractStatus.PENDING, ContractStatus.ACTIVE));

        if (hasActiveOrPending) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um contrato pendente ou ativo para este motorista e aluno");
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
        return userDriverContract;
    }

    @Transactional
    //nao valida aluno pq na hora de criar o contrato etc ja valida etc, nesse recinto.
    public void acceptContract(UUID contractId, UUID responsibleUserId) {
        UserDriverContract udc = userDriverContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        
        if (!udc.getResponsible().getUserId().equals(responsibleUserId)) {
            throw new AccessDeniedException("Usuário não tem permissão para aceitar este contrato");
        }

        Contract contract = udc.getContract();
        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contrato não está pendente");
        }

        contract.setStatus(ContractStatus.ACTIVE);
        contractRepository.save(contract);

        java.time.LocalDate ptr = contract.getStartDate();
        java.time.LocalDate end = contract.getEndDate();
        while (!ptr.isAfter(end)) {
            Payment p = new Payment();
            p.setContract(contract);
            p.setValue(contract.getValue());
            p.setPaymentDate(ptr);
            p.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(p);

            switch (contract.getPeriodicity()) {
                case WEEKLY -> ptr = ptr.plusWeeks(1);
                case MONTHLY -> ptr = ptr.plusMonths(1);
                case QUARTERLY -> ptr = ptr.plusMonths(3);
                case SEMI_ANNUALLY -> ptr = ptr.plusMonths(6);
                case ANNUALLY -> ptr = ptr.plusYears(1);
                default -> ptr = ptr.plusMonths(1);
            }
        }
    }

    @Transactional
    public void rejectContract(UUID contractId, UUID responsibleUserId) {
        UserDriverContract udc = userDriverContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        
        if (!udc.getResponsible().getUserId().equals(responsibleUserId)) {
            throw new AccessDeniedException("Usuário não tem permissão para rejeitar este contrato");
        }

        Contract contract = udc.getContract();
        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contrato não está pendente");
        }

        contract.setStatus(ContractStatus.CANCELED);
        contractRepository.save(contract);
    }

    // so as partes do contrato (responsavel e motorista) veem os detalhes
    @Transactional
    public ContractDetailsResponseDTO getContractDetails(UUID contractId, UUID userId) {
        UserDriverContract udc = userDriverContractRepository.findDetailsByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        if (!udc.getResponsible().getUserId().equals(userId) && !udc.getDriver().getUserId().equals(userId)) {
            throw new AccessDeniedException("Usuário não faz parte deste contrato");
        }

        Contract contract = udc.getContract();
        School school = udc.getSchool();
        Address address = school.getAddress();
        Document document = documentRepository
                .findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, contractId)
                .orElse(null);

        return new ContractDetailsResponseDTO(
                contract.getId(),
                udc.getId(),
                contract.getStatus(),
                contract.getPeriodicity(),
                contract.getValue(),
                contract.getStartDate(),
                contract.getEndDate(),
                udc.getStudent().getId(),
                udc.getStudent().getName(),
                school.getId(),
                school.getName(),
                address == null ? null : new AddressResponseDTO(
                        address.getId(),
                        address.getStreet(),
                        address.getZipCode(),
                        address.getCity(),
                        address.getNeighborhood(),
                        address.getNumber(),
                        address.getState(),
                        address.getLatitude(),
                        address.getLongitude()
                ),
                udc.getDriver().getUserId(),
                udc.getDriver().getUser().getName(),
                udc.getResponsible().getUserId(),
                udc.getResponsible().getUser().getName(),
                document == null ? null : document.getUrl(),
                document == null ? null : document.getType()
        );
    }

    // serve pros dois lados: motorista (montar a rota com os alunos ativos) e responsavel (ver os contratos dos filhos)
    @Transactional
    public List<ContractSummaryResponseDTO> findMyContracts(UUID userId, ContractStatus status) {
        List<UserDriverContract> contracts = status == null
                ? userDriverContractRepository.findAllOfUser(userId)
                : userDriverContractRepository.findAllOfUserByStatus(userId, status);
        return contracts.stream()
                .map(udc -> new ContractSummaryResponseDTO(
                        udc.getContract().getId(),
                        udc.getId(),
                        udc.getContract().getStatus(),
                        udc.getContract().getPeriodicity(),
                        udc.getContract().getValue(),
                        udc.getContract().getStartDate(),
                        udc.getContract().getEndDate(),
                        udc.getStudent().getId(),
                        udc.getStudent().getName(),
                        udc.getSchool().getId(),
                        udc.getSchool().getName(),
                        udc.getDriver().getUserId(),
                        udc.getDriver().getUser().getName(),
                        udc.getResponsible().getUserId(),
                        udc.getResponsible().getUser().getName()))
                .toList();
    }
}
