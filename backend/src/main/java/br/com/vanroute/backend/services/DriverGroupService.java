package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.group.DriverGroupRequestDTO;
import br.com.vanroute.backend.dtos.group.DriverGroupResponseDTO;
import br.com.vanroute.backend.dtos.group.GroupNoticeResponseDTO;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.group.DriverGroup;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverGroupRepository;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

// grupos de alunos do motorista: so ele ve e mexe. Avisar o grupo manda uma notificacao pra cada responsavel
// dos alunos do grupo (nao gera falta: e so um aviso)
@Service
public class DriverGroupService {

    private final DriverGroupRepository driverGroupRepository;
    private final DriverRepository driverRepository;
    private final StudentRepository studentRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final NotificationService notificationService;

    public DriverGroupService(DriverGroupRepository driverGroupRepository, DriverRepository driverRepository,
                              StudentRepository studentRepository, StudentResponsibleRepository studentResponsibleRepository,
                              UserDriverContractRepository userDriverContractRepository, NotificationService notificationService) {
        this.driverGroupRepository = driverGroupRepository;
        this.driverRepository = driverRepository;
        this.studentRepository = studentRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<DriverGroupResponseDTO> list(UUID driverId) {
        driver(driverId);
        return driverGroupRepository.findByDriverUserIdOrderByNameAsc(driverId).stream().map(DriverGroupService::toResponse).toList();
    }

    @Transactional
    public DriverGroupResponseDTO create(DriverGroupRequestDTO request, UUID driverId) {
        String name = request.name().trim();
        checkNameFree(driverId, name);
        DriverGroup group = new DriverGroup();
        group.setDriver(driver(driverId));
        group.setName(name);
        return toResponse(driverGroupRepository.save(group));
    }

    @Transactional
    public DriverGroupResponseDTO rename(UUID groupId, DriverGroupRequestDTO request, UUID driverId) {
        DriverGroup group = own(groupId, driverId);
        String name = request.name().trim();
        if (!group.getName().equalsIgnoreCase(name)) {
            checkNameFree(driverId, name);
        }
        group.setName(name);
        return toResponse(group);
    }

    @Transactional
    public void delete(UUID groupId, UUID driverId) {
        driverGroupRepository.delete(own(groupId, driverId));
    }

    // so aluno com contrato ativo com o motorista
    @Transactional
    public DriverGroupResponseDTO addStudent(UUID groupId, UUID studentId, UUID driverId) {
        DriverGroup group = own(groupId, driverId);
        if (userDriverContractRepository.findFirstByDriverUserIdAndStudentIdAndContractStatus(driverId, studentId, ContractStatus.ACTIVE).isEmpty()) {
            throw new AccessDeniedException("Não existe contrato ativo com esse aluno");
        }
        if (group.getStudents().stream().anyMatch(student -> student.getId().equals(studentId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno já está nesse grupo");
        }
        group.getStudents().add(studentRepository.getReferenceById(studentId));
        driverGroupRepository.flush();
        return toResponse(own(groupId, driverId));
    }

    @Transactional
    public DriverGroupResponseDTO removeStudent(UUID groupId, UUID studentId, UUID driverId) {
        DriverGroup group = own(groupId, driverId);
        if (!group.getStudents().removeIf(student -> student.getId().equals(studentId))) {
            throw new EntityNotFoundException("O aluno não está nesse grupo");
        }
        return toResponse(group);
    }

    // "nao vou buscar hoje": cada responsavel recebe uma notificacao com os filhos dele que estao no grupo
    @Transactional
    public GroupNoticeResponseDTO notice(UUID groupId, String reason, UUID driverId) {
        DriverGroup group = own(groupId, driverId);
        if (group.getStudents().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O grupo não tem alunos");
        }
        Map<UUID, List<Student>> childrenOf = new LinkedHashMap<>();
        group.getStudents().stream()
                .sorted(Comparator.comparing(Student::getName))
                .forEach(student -> studentResponsibleRepository.findResponsibleUserIds(student.getId())
                        .forEach(responsibleId -> childrenOf.computeIfAbsent(responsibleId, k -> new ArrayList<>()).add(student)));
        String driverName = group.getDriver().getUser().getName();
        String why = reason == null || reason.isBlank() ? "" : " Motivo: " + reason.trim();
        childrenOf.forEach((responsibleId, children) -> notificationService.send(List.of(responsibleId),
                NotificationType.DRIVER_NOTICE, "Aviso do motorista",
                driverName + " avisou que não vai buscar " + names(children) + " hoje." + why,
                Map.of("groupId", group.getId(), "driverId", driverId, "date", LocalDate.now(TripService.ZONE).toString(),
                        "studentIds", children.stream().map(Student::getId).toList())));
        return new GroupNoticeResponseDTO(childrenOf.size());
    }

    // "Ana", "Ana e Pedro", "Ana, Bia e Pedro"
    static String names(List<Student> students) {
        List<String> names = students.stream().map(Student::getName).toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
    }

    private void checkNameFree(UUID driverId, String name) {
        if (driverGroupRepository.existsByDriverUserIdAndNameIgnoreCase(driverId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já tem um grupo com esse nome");
        }
    }

    private Driver driver(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new AccessDeniedException("Só motorista tem grupos"));
    }

    private DriverGroup own(UUID groupId, UUID driverId) {
        return driverGroupRepository.findByIdAndDriverUserId(groupId, driverId)
                .orElseThrow(() -> new EntityNotFoundException("Grupo não encontrado"));
    }

    private static DriverGroupResponseDTO toResponse(DriverGroup group) {
        return new DriverGroupResponseDTO(group.getId(), group.getName(), group.getStudents().stream()
                .sorted(Comparator.comparing(Student::getName))
                .map(student -> new DriverGroupResponseDTO.Member(student.getId(), student.getName(), student.getPhotoKey()))
                .toList());
    }
}
