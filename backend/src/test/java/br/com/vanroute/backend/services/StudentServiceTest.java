package br.com.vanroute.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.dtos.student.AllStudentsFilterRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.student.enums.RelationType;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;

@ExtendWith(MockitoExtension.class)
public class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ResponsibleRepository responsibleRepository;

    @Mock
    private StudentResponsibleRepository studentResponsibleRepository;

    @InjectMocks
    private StudentService studentService;

    private Responsible responsible;

    @BeforeEach
    void setUp() {
        responsible = new Responsible();
        User u = new User();
        u.setCpf("12345678901");
        responsible.setUser(u);
    }

    @Test
    void testCreateStudentWithRelation_Success() {
        StudentRequestDTO requestDTO = new StudentRequestDTO("John Doe", "Notes", LocalDate.of(2010, 1, 1), RelationType.FINANCIAL);
        
        when(responsibleRepository.findByUserCpf("12345678901")).thenReturn(Optional.of(responsible));
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentResponsibleRepository.save(any(StudentResponsible.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentResponsible result = studentService.createStudentWithRelation(requestDTO, "12345678901");

        assertNotNull(result);
        assertTrue(result.isAdmin());
        assertEquals(RelationType.FINANCIAL, result.getRelationType());
        assertEquals("John Doe", result.getStudent().getName());
        assertEquals(responsible, result.getResponsible());
        
        verify(studentRepository, times(1)).save(any(Student.class));
        verify(studentResponsibleRepository, times(1)).save(any(StudentResponsible.class));
    }

    @Test
    void testCreateStudentWithRelation_ResponsibleNotFound() {
        StudentRequestDTO requestDTO = new StudentRequestDTO("John", "Notes", LocalDate.of(2010, 1, 1), RelationType.LEGAL);
        
        when(responsibleRepository.findByUserCpf("invalid")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            studentService.createStudentWithRelation(requestDTO, "invalid");
        });

        assertEquals("Responsible not found", exception.getMessage());
        verify(studentResponsibleRepository, never()).save(any());
    }

    @Test
    void testGetAllStudentsResponsible_Success() {
        AllStudentsFilterRequestDTO filter = new AllStudentsFilterRequestDTO(null, null, null);
        PageRequest pageable = PageRequest.of(0, 10);
        
        Student student = new Student();
        student.setName("Student 1");
        
        StudentResponsible sr = new StudentResponsible();
        sr.setStudent(student);
        sr.setResponsible(responsible);
        
        Page<StudentResponsible> page = new PageImpl<>(Collections.singletonList(sr));
        
        when(studentResponsibleRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        
        Page<Student> resultPage = studentService.getAllStudentsResponsible(filter, pageable, "12345678901");
        
        assertNotNull(resultPage);
        assertEquals(1, resultPage.getContent().size());
        assertEquals("Student 1", resultPage.getContent().get(0).getName());
    }
}
