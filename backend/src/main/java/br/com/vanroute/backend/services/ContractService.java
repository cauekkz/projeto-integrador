package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.contract.MyContractsFilterRequestDTO;

import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.PageImpl;

import org.springframework.data.domain.Page;

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
import br.com.vanroute.backend.repositories.SignatureRepository;
import br.com.vanroute.backend.models.document.Signature;
import br.com.vanroute.backend.models.document.enums.DocumentEntityType;
import br.com.vanroute.backend.models.document.enums.DocumentStatus;
import br.com.vanroute.backend.models.document.enums.DocumentType;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.dtos.contract.CancellationResponseDTO;
import br.com.vanroute.backend.repositories.ContractCancellationRepository;
import br.com.vanroute.backend.repositories.ContractRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.models.contract.Payment;
import br.com.vanroute.backend.models.contract.enums.ContractPeriodicity;
import br.com.vanroute.backend.models.contract.enums.PaymentStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final DocumentRepository documentRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final DriverSchoolsRepository driverSchoolsRepository;
    private final PaymentRepository paymentRepository;
    private final StudentResponsibleService studentResponsibleService;
    private final EntityManager entityManager;
    private final SignatureRepository signatureRepository;
    private final StorageService storageService;
    private final ContractCancellationRepository contractCancellationRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;

    public ContractService(
            ContractRepository contractRepository,
            DocumentRepository documentRepository,
            UserDriverContractRepository userDriverContractRepository,
            DriverSchoolsRepository driverSchoolsRepository,
            PaymentRepository paymentRepository,
            StudentResponsibleService studentResponsibleService,
            EntityManager entityManager,
            SignatureRepository signatureRepository,
            StorageService storageService,
            ContractCancellationRepository contractCancellationRepository,
            StudentResponsibleRepository studentResponsibleRepository) {
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.signatureRepository = signatureRepository;
        this.contractCancellationRepository = contractCancellationRepository;
        this.storageService = storageService;
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
        validateTerms(request.contract());
        // toda proposta tem o PDF do contrato, ja enviado pro storage
        DocumentRequestDTO documentDto = request.document();
        if (documentDto == null || documentDto.url() == null || documentDto.url().isBlank()) {
            throw new BadRequestException("Anexe o PDF do contrato à proposta");
        }
        if (documentDto.type() != null && !DocumentType.TRANSPORT_CONTRACT.name().equals(documentDto.type())) {
            throw new BadRequestException("O documento da proposta precisa ser do tipo TRANSPORT_CONTRACT");
        }
        if (!StorageService.isOwnedBy(documentDto.url(), driverId)) {
            throw new BadRequestException("O PDF do contrato precisa ser um arquivo enviado por você");
        }
        if (!studentResponsibleService.isAdmin(request.responsibleId(), request.studentId())) {
            throw new BadRequestException("O Responsável não possui conexão com a criança informada.");
        }

        boolean isDriverLinkedToSchool = driverSchoolsRepository
                .existsByDriverUserIdAndSchoolId(driverId, request.schoolId());
        
        if (!isDriverLinkedToSchool) {
            throw new AccessDeniedException("Motorista não possui essa escola");
        }

        // trava o aluno ate o fim da transacao: duas propostas ao mesmo tempo pro mesmo aluno (duplo clique)
        // passam uma de cada vez, e a segunda ja ve a primeira
        Student studentRef = entityManager.find(Student.class, request.studentId(), LockModeType.PESSIMISTIC_WRITE);
        if (studentRef == null) {
            throw new EntityNotFoundException("Aluno não encontrado");
        }
        ContractRequestDTO contractDto = request.contract();
        if (userDriverContractRepository.existsPendingOrOverlapping(driverId, request.studentId(),
                contractDto.startDate(), contractDto.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma proposta pendente, um contrato agendado ou um contrato ativo nesse período para este motorista e aluno");
        }

        Driver driverRef = entityManager.getReference(Driver.class, driverId);
        School schoolRef = entityManager.getReference(School.class, request.schoolId());
        Responsible responsibleRef = entityManager.getReference(Responsible.class, request.responsibleId());
        User uploaderUserRef = entityManager.getReference(User.class, driverId);

        Contract contract = new Contract();
        contract.setPeriodicity(contractDto.periodicity());
        contract.setValue(contractDto.value());
        contract.setStartDate(contractDto.startDate());
        contract.setEndDate(contractDto.endDate());
        contract.setStatus(ContractStatus.PENDING);
        
        contract = contractRepository.save(contract);

        Document document = new Document();
        // PDF conferido e copiado pra uma pasta em que ninguem grava: o que foi assinado nao muda depois
        document.setUrl(storageService.freezeContractPdf(documentDto.url(), contract.getId()));
        document.setEntityId(contract.getId());
        // documento de proposta e sempre o contrato
        document.setType(DocumentType.TRANSPORT_CONTRACT);

        document.setStatus(DocumentStatus.PENDING);
        document.setEntityType(DocumentEntityType.CONTRACT);
        document.setUploadedAt(LocalDateTime.now(TripService.ZONE));
        document.setUploadedBy(uploaderUserRef);

        documentRepository.save(document);
        // propor e assinar: o motorista assina os termos que mandou
        sign(document, uploaderUserRef, termsHash(contract, driverId, request.responsibleId(), request.studentId(),
                request.schoolId(), document.getUrl()));

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
    // aluno, escola e vinculo ja foram validados na proposta
    public void acceptContract(UUID contractId, UUID responsibleUserId) {
        // primeiro trava o contrato: um segundo aceite ao mesmo tempo espera e ja encontra o contrato aceito
        Contract contract = lockContract(contractId);
        UserDriverContract udc = userDriverContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
        
        if (!udc.getResponsible().getUserId().equals(responsibleUserId)) {
            throw new AccessDeniedException("Usuário não tem permissão para aceitar este contrato");
        }

        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contrato não está pendente");
        }
        LocalDate today = LocalDate.now(TripService.ZONE);
        // proposta que passou da data de inicio geraria parcelas ja vencidas: o motorista manda outra
        if (contract.getStartDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A data de início desta proposta já passou. Peça ao motorista uma nova proposta");
        }

        // comeca hoje: ACTIVE. Comeca depois: SCHEDULED, e o worker passa pra ACTIVE no dia (so entao o aluno
        // pode entrar nas rotas)
        contract.setStatus(contract.getStartDate().isAfter(today) ? ContractStatus.SCHEDULED : ContractStatus.ACTIVE);
        contractRepository.save(contract);
        // aceitar e assinar: o responsavel assina os mesmos termos e o documento fica assinado pelas duas partes
        documentRepository.findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, contractId).ifPresent(document -> {
            sign(document, udc.getResponsible().getUser(), termsHash(udc, document.getUrl()));
            document.setStatus(DocumentStatus.SIGNED);
        });

        List<Payment> payments = new java.util.ArrayList<>();
        for (LocalDate dueDate : installmentDates(contract.getPeriodicity(), contract.getStartDate(), contract.getEndDate())) {
            Payment p = new Payment();
            p.setContract(contract);
            p.setValue(contract.getValue());
            p.setDueDate(dueDate);
            p.setStatus(PaymentStatus.PENDING);
            payments.add(p);
        }
        paymentRepository.saveAll(payments);
    }

    private Contract lockContract(UUID contractId) {
        return contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));
    }

    static final int MAX_CONTRACT_YEARS = 2;
    // NUMERIC(10,2) do banco
    private static final BigDecimal MAX_CONTRACT_VALUE = new BigDecimal("99999999.99");

    static void validateTerms(ContractRequestDTO terms) {
        if (terms == null || terms.periodicity() == null || terms.value() == null
                || terms.startDate() == null || terms.endDate() == null) {
            throw new BadRequestException("Informe periodicidade, valor, data de início e data de fim do contrato");
        }
        if (terms.value().signum() <= 0) {
            throw new BadRequestException("O valor do contrato precisa ser maior que zero");
        }
        if (terms.value().stripTrailingZeros().scale() > 2 || terms.value().compareTo(MAX_CONTRACT_VALUE) > 0) {
            throw new BadRequestException("Valor do contrato inválido");
        }
        if (terms.startDate().isBefore(LocalDate.now(TripService.ZONE))) {
            throw new BadRequestException("O contrato não pode começar numa data que já passou");
        }
        if (terms.endDate().isBefore(terms.startDate())) {
            throw new BadRequestException("A data de fim precisa ser depois da data de início");
        }
        if (terms.endDate().isAfter(terms.startDate().plusYears(MAX_CONTRACT_YEARS))) {
            throw new BadRequestException("O contrato pode durar no máximo " + MAX_CONTRACT_YEARS + " anos");
        }
    }

    // cada vencimento sai da data de inicio (inicio + n periodos), e nao do anterior,
    // pra um contrato do dia 31 nao virar dia 28 pra sempre depois de fevereiro
    static List<LocalDate> installmentDates(ContractPeriodicity periodicity, LocalDate start, LocalDate end) {
        List<LocalDate> dates = new java.util.ArrayList<>();
        for (int n = 0; ; n++) {
            LocalDate dueDate = switch (periodicity) {
                case WEEKLY -> start.plusWeeks(n);
                case MONTHLY -> start.plusMonths(n);
                case QUARTERLY -> start.plusMonths(3L * n);
                case SEMI_ANNUALLY -> start.plusMonths(6L * n);
                case ANNUALLY -> start.plusYears(n);
            };
            if (dueDate.isAfter(end)) {
                return dates;
            }
            dates.add(dueDate);
        }
    }

    @Transactional
    public void rejectContract(UUID contractId, UUID responsibleUserId) {
        lockContract(contractId);
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
        documentRepository.findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, contractId)
                .ifPresent(document -> document.setStatus(DocumentStatus.REJECTED));
    }

    // o motorista desiste da proposta antes do responsavel decidir
    @Transactional
    public void withdrawContract(UUID contractId, UUID driverId) {
        lockContract(contractId);
        UserDriverContract udc = userDriverContractRepository.findByContractId(contractId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado"));

        if (!udc.getDriver().getUserId().equals(driverId)) {
            throw new AccessDeniedException("Só o motorista que fez a proposta pode retirá-la");
        }

        Contract contract = udc.getContract();
        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contrato não está pendente");
        }

        contract.setStatus(ContractStatus.CANCELED);
        contractRepository.save(contract);
        documentRepository.findFirstByEntityTypeAndEntityId(DocumentEntityType.CONTRACT, contractId)
                .ifPresent(document -> document.setStatus(DocumentStatus.REJECTED));
    }

    private void sign(Document document, User user, String hash) {
        Signature signature = new Signature();
        signature.setDocument(document);
        signature.setUser(user);
        signature.setSignedAt(LocalDateTime.now(TripService.ZONE));
        signature.setDocumentHash(hash);
        signatureRepository.save(signature);
    }

    private static String termsHash(UserDriverContract udc, String documentUrl) {
        return termsHash(udc.getContract(), udc.getDriver().getUserId(), udc.getResponsible().getUserId(),
                udc.getStudent().getId(), udc.getSchool().getId(), documentUrl);
    }

    // SHA-256 dos termos (partes, aluno, escola, valor, periodo, documento): o que cada um assinou.
    // Se alguem mudar o contrato no banco, o hash recalculado nao bate mais com o das assinaturas
    static String termsHash(Contract contract, UUID driverId, UUID responsibleId, UUID studentId, UUID schoolId, String documentUrl) {
        String terms = String.join("|", String.valueOf(contract.getId()), String.valueOf(driverId), String.valueOf(responsibleId),
                String.valueOf(studentId), String.valueOf(schoolId), String.valueOf(contract.getPeriodicity()),
                contract.getValue() == null ? "null" : contract.getValue().stripTrailingZeros().toPlainString(),
                String.valueOf(contract.getStartDate()),
                String.valueOf(contract.getEndDate()), String.valueOf(documentUrl));
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(terms.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
                document == null ? null : document.getType(),
                document == null ? null : document.getStatus(),
                document == null ? List.of() : signatureRepository.findByDocumentIdOrderBySignedAtAsc(document.getId()).stream()
                        .map(signature -> new ContractDetailsResponseDTO.SignatureItem(signature.getUser().getId(),
                                signature.getUser().getName(), signature.getSignedAt(),
                                signature.getDocumentHash().equals(termsHash(udc, document.getUrl()))))
                        .toList(),
                contractCancellationRepository.findHistory(contractId).stream().map(CancellationResponseDTO::of).toList(),
                paymentRepository.countOverdue(contractId, LocalDate.now(TripService.ZONE)),
                studentResponsibleRepository.findLinksOfStudent(udc.getStudent().getId()).stream()
                        .map(link -> new ContractDetailsResponseDTO.StudentResponsibleItem(link.getResponsible().getUserId(),
                                link.getResponsible().getUser().getName(), link.getRelationType(), link.isAdmin()))
                        .toList()
        );
    }

    // serve pros dois lados: motorista (montar a rota com os alunos ativos) e responsavel (ver os contratos dos filhos)
    @Transactional
    public Page<ContractSummaryResponseDTO> findMyContracts(UUID userId, MyContractsFilterRequestDTO filter, Pageable pageable) {
        // so as condicoes dos filtros que vieram (parametro UUID nulo nao funciona bem no Postgres)
        StringBuilder where = new StringBuilder(" WHERE (d.userId = :me OR r.userId = :me)");
        Map<String, Object> params = new HashMap<>();
        params.put("me", userId);
        if (filter != null && filter.status() != null) {
            where.append(" AND c.status = :status");
            params.put("status", filter.status());
        }
        if (filter != null && filter.studentId() != null) {
            where.append(" AND s.id = :studentId");
            params.put("studentId", filter.studentId());
        }
        if (filter != null && filter.driverId() != null) {
            where.append(" AND d.userId = :driverId");
            params.put("driverId", filter.driverId());
        }
        var query = entityManager.createQuery("SELECT u FROM UserDriverContract u JOIN FETCH u.contract c JOIN FETCH u.student s " +
                "JOIN FETCH u.school JOIN FETCH u.driver d JOIN FETCH d.user JOIN FETCH u.responsible r JOIN FETCH r.user" + where +
                " ORDER BY c.startDate DESC, c.id", UserDriverContract.class);
        var count = entityManager.createQuery("SELECT COUNT(u) FROM UserDriverContract u JOIN u.contract c JOIN u.student s " +
                "JOIN u.driver d JOIN u.responsible r" + where, Long.class);
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            count.setParameter(name, value);
        });
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<UserDriverContract> contracts = query.getResultList();
        List<UUID> contractIds = contracts.stream().map(udc -> udc.getContract().getId()).toList();
        Map<UUID, CancellationResponseDTO> pending = new HashMap<>();
        Map<UUID, Long> overdue = new HashMap<>();
        if (!contractIds.isEmpty()) {
            contractCancellationRepository.findPendingOf(contractIds)
                    .forEach(c -> pending.put(c.getContract().getId(), CancellationResponseDTO.of(c)));
            paymentRepository.countOverdueOf(contractIds, LocalDate.now(TripService.ZONE))
                    .forEach(row -> overdue.put((UUID) row[0], (Long) row[1]));
        }
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
                        udc.getResponsible().getUser().getName(),
                        pending.get(udc.getContract().getId()),
                        overdue.getOrDefault(udc.getContract().getId(), 0L)))
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                        page -> new PageImpl<>(page, pageable, count.getSingleResult())));
    }
}
