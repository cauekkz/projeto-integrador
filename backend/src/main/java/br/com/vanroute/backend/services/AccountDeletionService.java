package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.PaymentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import br.com.vanroute.backend.utils.CodeGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.redis.core.RedisTemplate;
import br.com.vanroute.backend.websocket.WebSocketSessionRegistry;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// "excluir minha conta" (LGPD): apaga os dados pessoais e mantem a linha como "Usuario removido", porque
// contratos, parcelas, chat e viagens dos outros apontam pra ela. Apaga tambem os arquivos que a pessoa enviou
// (anexos do chat e fotos; o texto das mensagens fica no historico da outra parte) e fecha as sessoes WebSocket.
// Recusa com contrato aceito, proposta pendente ou parcela atrasada: a pessoa cancela (aviso previo) ou quita antes
@Service
public class AccountDeletionService {

    static final String REMOVED_NAME = "Usuário removido";
    static final String REMOVED_STUDENT_NAME = "Aluno removido";

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final PaymentRepository paymentRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final StorageService storageService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final CodeGenerator codeGenerator;
    private final EntityManager entityManager;
    private final RedisTemplate<String, String> redisTemplate;

    public AccountDeletionService(UserRepository userRepository, DriverRepository driverRepository,
                                  UserDriverContractRepository userDriverContractRepository, PaymentRepository paymentRepository,
                                  StudentResponsibleRepository studentResponsibleRepository, StorageService storageService,
                                  RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder,
                                  CodeGenerator codeGenerator, EntityManager entityManager,
                                  RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.paymentRepository = paymentRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.storageService = storageService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.codeGenerator = codeGenerator;
        this.entityManager = entityManager;
    }

    @Transactional
    public void deleteMyAccount(UUID userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        // confirma com a senha: um token roubado sozinho nao apaga a conta
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Senha incorreta");
        }
        if (userDriverContractRepository.existsOfUserWithStatus(userId,
                List.of(ContractStatus.ACTIVE, ContractStatus.SCHEDULED, ContractStatus.PENDING))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cancele os contratos ativos e as propostas pendentes antes de excluir a conta");
        }
        if (paymentRepository.countOverdueOfUser(userId, LocalDate.now(TripService.ZONE)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há parcelas atrasadas. Elas precisam ser quitadas antes de excluir a conta");
        }

        LocalDate today = LocalDate.now(TripService.ZONE);
        driverRepository.findById(userId).ifPresent(driver -> {
            // o codigo de conversa antigo para de funcionar e a chave Pix sai
            driver.setLinkCode(newLinkCode());
            driver.setPix(null, null);
            native_("UPDATE route_drivers SET end_date = :today WHERE driver_id = :id AND end_date IS NULL", userId, today);
            native_("UPDATE driver_vehicles SET end_date = :today WHERE driver_id = :id AND end_date IS NULL", userId, today);
            native_("DELETE FROM driver_schools WHERE driver_id = :id", userId, null);
            // os grupos levam junto os alunos deles (ON DELETE CASCADE)
            native_("DELETE FROM driver_groups WHERE driver_id = :id", userId, null);
        });
        if (entityManager.find(br.com.vanroute.backend.models.user.Responsible.class, userId) != null) {
            // endereco de cobranca e pessoal
            native_("UPDATE responsibles SET address_id = NULL WHERE user_id = :id", userId, null);
            leaveChildren(userId);
        }
        native_("DELETE FROM notifications WHERE user_id = :id", userId, null);
        // os arquivos somem do storage: as mensagens ficam sem o anexo (mensagem so de anexo vira "[arquivo removido]")
        native_("UPDATE chat_messages SET content = COALESCE(content, '[arquivo removido]'), attachment_url = NULL, "
                + "attachment_type = NULL WHERE sender_user_id = :id AND attachment_url IS NOT NULL", userId, null);

        user.setPhotoKey(null);
        user.setName(REMOVED_NAME);
        user.setCpf(null);
        user.setEmail(null);
        user.setPhone(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        // INACTIVE: o filtro do JWT recusa na hora, o refresh nao renova e o login nao acha (sem CPF)
        user.setStatus(UserStatus.INACTIVE);
        refreshTokenService.revokeAll(userId);
        // depois do commit: se algo acima falhar, os arquivos e as sessoes continuam
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageService.deleteFolder(StorageService.UPLOAD_PREFIX + userId + "/");
                storageService.deleteFolder(StorageService.PROFILE_PREFIX + userId + "/");
                redisTemplate.convertAndSend(WebSocketSessionRegistry.REVOKE_CHANNEL, userId.toString());
            }
        });
    }

    // sai dos filhos: se tem outro responsavel, ele continua (e vira admin se quem saiu era o admin);
    // se era o unico, o aluno fica anonimo e sem enderecos (o historico de contrato e viagem continua apontando pra ele)
    private void leaveChildren(UUID userId) {
        List<StudentResponsible> mine = entityManager.createQuery(
                        "SELECT sr FROM StudentResponsible sr WHERE sr.responsible.userId = :id", StudentResponsible.class)
                .setParameter("id", userId).getResultList();
        for (StudentResponsible link : mine) {
            UUID studentId = link.getStudent().getId();
            List<StudentResponsible> others = studentResponsibleRepository.findLinksOfStudent(studentId).stream()
                    .filter(other -> !other.getResponsible().getUserId().equals(userId))
                    .toList();
            if (others.isEmpty()) {
                link.getStudent().setName(REMOVED_STUDENT_NAME);
                native_("DELETE FROM student_addresses WHERE student_id = :id", studentId, null);
            } else if (link.isAdmin() && others.stream().noneMatch(StudentResponsible::isAdmin)) {
                others.get(0).setAdmin(true);
            }
            studentResponsibleRepository.delete(link);
        }
    }

    private String newLinkCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = codeGenerator.generateCode();
            if (driverRepository.findUserIdByLinkCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de conversa único");
    }

    private void native_(String sql, UUID id, LocalDate today) {
        var query = entityManager.createNativeQuery(sql).setParameter("id", id);
        if (today != null) {
            query.setParameter("today", today);
        }
        query.executeUpdate();
    }
}
