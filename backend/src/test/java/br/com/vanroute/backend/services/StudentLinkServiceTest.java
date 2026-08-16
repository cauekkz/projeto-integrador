package br.com.vanroute.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.vanroute.backend.dtos.student.StudentLinkRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.student.enums.RelationType;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;

@ExtendWith(MockitoExtension.class)
public class StudentLinkServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StudentResponsibleRepository studentResponsibleRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ResponsibleRepository responsibleRepository;

    @InjectMocks
    private StudentLinkService studentLinkService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGenerateStudentCodeToLink_Success() throws JsonProcessingException {
        UUID studentId = UUID.randomUUID();
        StudentLinkRequestDTO dto = new StudentLinkRequestDTO(studentId, RelationType.FINANCIAL);
        
        when(studentResponsibleRepository.existsByResponsible_User_CpfAndStudent_IdAndIsAdminTrue("12345678901", studentId))
                .thenReturn(true);
        when(objectMapper.writeValueAsString(dto)).thenReturn("json_string");
        when(valueOperations.setIfAbsent(anyString(), eq("json_string"), any(Duration.class))).thenReturn(true);

        String code = studentLinkService.generateStudentCodeToLink(dto, "12345678901");
        
        assertNotNull(code);
        assertEquals(9, code.length());
        verify(valueOperations, times(1)).setIfAbsent(anyString(), eq("json_string"), any(Duration.class));
    }

    @Test
    void testGenerateStudentCodeToLink_NotAdmin() {
        UUID studentId = UUID.randomUUID();
        StudentLinkRequestDTO dto = new StudentLinkRequestDTO(studentId, RelationType.LEGAL);
        
        when(studentResponsibleRepository.existsByResponsible_User_CpfAndStudent_IdAndIsAdminTrue("123", studentId))
                .thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> studentLinkService.generateStudentCodeToLink(dto, "123"));
        assertEquals("O Responsavel não tem essa permissão", exception.getMessage());
    }

    @Test
    void testCreateStudentResponsibleRelation_Success() throws JsonProcessingException {
        UUID studentId = UUID.randomUUID();
        StudentLinkRequestDTO dto = new StudentLinkRequestDTO(studentId, RelationType.LEGAL);
        
        Student student = new Student();
        Responsible responsible = new Responsible();
        
        when(valueOperations.get("student:link:CODE123")).thenReturn("saved_json");
        when(objectMapper.readValue("saved_json", StudentLinkRequestDTO.class)).thenReturn(dto);
        when(studentRepository.getReferenceById(studentId)).thenReturn(student);
        when(responsibleRepository.findByUserCpf("12345678901")).thenReturn(Optional.of(responsible));
        
        studentLinkService.createStudentResponsibleRelation("CODE123", "12345678901");
        
        verify(studentResponsibleRepository, times(1)).save(any(StudentResponsible.class));
        verify(redisTemplate, times(1)).delete("student:link:CODE123");
    }

    @Test
    void testCreateStudentResponsibleRelation_InvalidCode() {
        when(valueOperations.get("student:link:INVALID")).thenReturn(null);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            studentLinkService.createStudentResponsibleRelation("INVALID", "123");
        });
        
        assertEquals("Código incorreto ou expirado.", exception.getMessage());
    }
}
