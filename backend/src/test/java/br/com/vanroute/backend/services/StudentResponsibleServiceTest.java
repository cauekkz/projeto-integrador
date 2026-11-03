package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentResponsibleServiceTest {

    @Mock
    private StudentResponsibleRepository studentResponsibleRepository;
    @Mock
    private UserDriverContractRepository userDriverContractRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StudentResponsibleService service;

    private final UUID studentId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private StudentResponsible admin;
    private StudentResponsible other;

    @BeforeEach
    void setUp() {
        Student student = new Student();
        student.setId(studentId);
        student.setName("Ana");
        admin = link(student, adminId, true);
        other = link(student, otherId, false);
    }

    private StudentResponsible link(Student student, UUID responsibleId, boolean isAdmin) {
        StudentResponsible link = new StudentResponsible();
        link.setStudent(student);
        link.setAdmin(isAdmin);
        when(studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(responsibleId, studentId)).thenReturn(Optional.of(link));
        return link;
    }

    @Test
    void adminRemovesAnotherResponsibleWhoIsNotified() {
        service.remove(studentId, otherId, adminId);

        verify(studentResponsibleRepository).delete(other);
        verify(notificationService).send(eq(List.of(otherId)), eq(NotificationType.SYSTEM), anyString(), anyString(), anyMap());
    }

    @Test
    void anyoneLeavesButTheAdminPassesTheAdminFirst() {
        service.remove(studentId, otherId, otherId);
        verify(studentResponsibleRepository).delete(other);

        assertThrows(ResponseStatusException.class, () -> service.remove(studentId, adminId, adminId));
        verify(studentResponsibleRepository, never()).delete(admin);
    }

    @Test
    void nonAdminCannotRemoveOthers() {
        assertThrows(AccessDeniedException.class, () -> service.remove(studentId, adminId, otherId));
        assertThrows(AccessDeniedException.class, () -> service.remove(studentId, otherId, UUID.randomUUID()));
    }

    @Test
    void whoHasAContractWithTheStudentCannotLeave() {
        when(userDriverContractRepository.existsByResponsibleUserIdAndStudentIdAndContractStatusIn(eq(otherId), eq(studentId), any()))
                .thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> service.remove(studentId, otherId, adminId));
        verify(studentResponsibleRepository, never()).delete(any(StudentResponsible.class));
    }

    @Test
    void adminPassesTheAdmin() {
        service.transferAdmin(studentId, otherId, adminId);

        assertFalse(admin.isAdmin());
        assertTrue(other.isAdmin());
    }

    @Test
    void onlyTheAdminPassesTheAdmin() {
        assertThrows(AccessDeniedException.class, () -> service.transferAdmin(studentId, adminId, otherId));
        assertTrue(admin.isAdmin());
        assertEquals(false, other.isAdmin());
    }
}
