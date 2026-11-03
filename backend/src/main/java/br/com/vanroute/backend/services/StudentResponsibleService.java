package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.student.StudentResponsibleLinkDTO;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// vinculos responsavel-aluno. O admin (quem cadastrou o aluno, ou quem recebeu o admin) convida, remove os outros
// e passa o admin adiante; qualquer um sai sozinho, menos o admin (passa o admin antes).
// Divida nao muda de dono com nada disso: a parcela e do contrato, e o contrato e de quem assinou
@Service
public class StudentResponsibleService {

    // quem assinou contrato desses status com o aluno nao sai nem e removido (cancela o contrato antes)
    private static final List<ContractStatus> OPEN_CONTRACT = List.of(ContractStatus.PENDING, ContractStatus.SCHEDULED, ContractStatus.ACTIVE);

    private final StudentResponsibleRepository studentResponsibleRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final NotificationService notificationService;

    public StudentResponsibleService(StudentResponsibleRepository studentResponsibleRepository,
                                     UserDriverContractRepository userDriverContractRepository,
                                     NotificationService notificationService) {
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.notificationService = notificationService;
    }

    public boolean isAdmin(UUID responsibleId, UUID studentId) {
        return studentResponsibleRepository
                .existsByResponsible_UserIdAndStudent_IdAndIsAdminTrue(
                        responsibleId,
                        studentId
                );
    }

    // responsaveis do aluno (qualquer responsavel vinculado ve)
    @Transactional(readOnly = true)
    public List<StudentResponsibleLinkDTO> list(UUID studentId, UUID userId) {
        link(userId, studentId);
        return studentResponsibleRepository.findLinksOfStudent(studentId).stream()
                .map(sr -> new StudentResponsibleLinkDTO(sr.getResponsible().getUserId(), sr.getResponsible().getUser().getName(),
                        sr.getRelationType(), sr.isAdmin()))
                .toList();
    }

    // o admin remove outro responsavel, ou um responsavel sai sozinho. Quem sai perde na hora o acesso as rotas,
    // viagens (inclusive a localizacao ao vivo) e historico do aluno
    @Transactional
    public void remove(UUID studentId, UUID responsibleId, UUID userId) {
        StudentResponsible caller = link(userId, studentId);
        boolean self = userId.equals(responsibleId);
        if (!self && !caller.isAdmin()) {
            throw new AccessDeniedException("Só o responsável admin remove outros responsáveis");
        }
        StudentResponsible target = self ? caller : studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(responsibleId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Esse responsável não está vinculado ao aluno"));
        if (target.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O admin não pode sair: passe o admin para outro responsável antes");
        }
        if (userDriverContractRepository.existsByResponsibleUserIdAndStudentIdAndContractStatusIn(responsibleId, studentId, OPEN_CONTRACT)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esse responsável tem contrato ou proposta em andamento com este aluno: cancele antes");
        }
        String studentName = target.getStudent().getName();
        studentResponsibleRepository.delete(target);
        if (!self) {
            notificationService.send(List.of(responsibleId), NotificationType.SYSTEM, "Vínculo removido",
                    "Você não está mais vinculado a " + studentName, Map.of("studentId", studentId));
        }
    }

    // o admin passa o admin para outro responsavel ja vinculado (e deixa de ser admin)
    @Transactional
    public void transferAdmin(UUID studentId, UUID responsibleId, UUID userId) {
        StudentResponsible caller = link(userId, studentId);
        if (!caller.isAdmin()) {
            throw new AccessDeniedException("Só o responsável admin passa o admin");
        }
        if (userId.equals(responsibleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já é o admin");
        }
        StudentResponsible target = studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(responsibleId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Esse responsável não está vinculado ao aluno"));
        caller.setAdmin(false);
        target.setAdmin(true);
        notificationService.send(List.of(responsibleId), NotificationType.SYSTEM, "Agora você é o admin",
                "Você passou a ser o responsável admin de " + target.getStudent().getName(), Map.of("studentId", studentId));
    }

    private StudentResponsible link(UUID userId, UUID studentId) {
        return studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(userId, studentId)
                .orElseThrow(() -> new AccessDeniedException("Responsável não vinculado a esse aluno"));
    }
}
