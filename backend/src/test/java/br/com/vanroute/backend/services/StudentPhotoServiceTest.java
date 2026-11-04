package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentPhotoServiceTest {

    @Mock
    private StudentResponsibleRepository studentResponsibleRepository;
    @Mock
    private StorageService storageService;
    @InjectMocks
    private StudentPhotoService service;

    private final UUID studentId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private Student student;

    @BeforeEach
    void setUp() {
        student = new Student();
        student.setId(studentId);
        when(studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(adminId, studentId)).thenReturn(Optional.of(link(true)));
        when(studentResponsibleRepository.findByResponsible_UserIdAndStudent_Id(otherId, studentId)).thenReturn(Optional.of(link(false)));
    }

    private StudentResponsible link(boolean admin) {
        StudentResponsible link = new StudentResponsible();
        link.setStudent(student);
        link.setAdmin(admin);
        return link;
    }

    @Test
    void adminSetsThePhotoAndTheOldOneIsDeleted() {
        student.setPhotoKey("students/" + studentId + "/velha.jpg");
        String key = "students/" + studentId + "/nova.jpg";

        assertEquals(key, service.setPhoto(studentId, adminId, key));

        assertEquals(key, student.getPhotoKey());
        verify(storageService).checkUploaded(key);
        verify(storageService).delete("students/" + studentId + "/velha.jpg");
    }

    @Test
    void onlyThePhotoOfThisStudentIsAccepted() {
        assertThrows(BadRequestException.class, () -> service.setPhoto(studentId, adminId, "students/" + UUID.randomUUID() + "/x.jpg"));
        assertThrows(BadRequestException.class, () -> service.setPhoto(studentId, adminId, "uploads/" + adminId + "/x.jpg"));
        assertThrows(BadRequestException.class, () -> service.setPhoto(studentId, adminId, null));
        assertNull(student.getPhotoKey());
    }

    @Test
    void onlyTheAdminChangesThePhoto() {
        assertThrows(AccessDeniedException.class, () -> service.setPhoto(studentId, otherId, "students/" + studentId + "/x.jpg"));
        assertThrows(AccessDeniedException.class, () -> service.uploadUrl(studentId, otherId, "x.jpg", "image/jpeg"));
        assertThrows(AccessDeniedException.class, () -> service.setPhoto(studentId, UUID.randomUUID(), "students/" + studentId + "/x.jpg"));
        verify(storageService, never()).generateStudentPhotoUploadUrl(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void removingDeletesTheFile() {
        student.setPhotoKey("students/" + studentId + "/x.jpg");

        service.removePhoto(studentId, adminId);

        assertNull(student.getPhotoKey());
        verify(storageService).delete("students/" + studentId + "/x.jpg");
    }
}
