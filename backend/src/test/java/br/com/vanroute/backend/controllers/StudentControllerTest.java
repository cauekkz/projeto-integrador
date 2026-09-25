package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.student.StudentLinkRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentCodeRequestDTO;
import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.models.student.enums.RelationType;
import br.com.vanroute.backend.services.StudentLinkService;
import br.com.vanroute.backend.services.StudentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class StudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentService studentService;

    @Mock
    private StudentLinkService studentLinkService;

    @InjectMocks
    private StudentController studentController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(studentController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void testCreateStudentWithRelation() throws Exception {
        StudentRequestDTO req = new StudentRequestDTO("Alice", "Note", LocalDate.of(2012, 1, 1), RelationType.FINANCIAL);

        Student student = new Student();
        student.setName("Alice");

        StudentResponsible sr = new StudentResponsible();
        sr.setStudent(student);
        sr.setRelationType(RelationType.FINANCIAL);

        when(studentService.createStudentWithRelation(any(), eq("12345678901"))).thenReturn(sr);

        mockMvc.perform(post("/api/student/create-student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .principal(new UsernamePasswordAuthenticationToken("12345678901", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationType").value("FINANCIAL"));

        verify(studentService, times(1)).createStudentWithRelation(any(), eq("12345678901"));
    }

    @Test
    void testGenerateStudentCodeToLink() throws Exception {
        StudentLinkRequestDTO req = new StudentLinkRequestDTO(UUID.randomUUID(), RelationType.LEGAL);

        when(studentLinkService.generateStudentCodeToLink(any(), eq("12345678901"))).thenReturn("CODE123");

        mockMvc.perform(post("/api/student/generate-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .principal(new UsernamePasswordAuthenticationToken("12345678901", null)))
                .andExpect(status().isOk())
                .andExpect(content().string("CODE123"));

        verify(studentLinkService, times(1)).generateStudentCodeToLink(any(), eq("12345678901"));
    }

    @Test
    void testConfirmLink() throws Exception {
        StudentCodeRequestDTO req = new StudentCodeRequestDTO("CODE123");

        doNothing().when(studentLinkService).createStudentResponsibleRelation("CODE123", "12345678901");

        mockMvc.perform(post("/api/student/confirm-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .principal(new UsernamePasswordAuthenticationToken("12345678901", null)))
                .andExpect(status().isCreated());

        verify(studentLinkService, times(1)).createStudentResponsibleRelation("CODE123", "12345678901");
    }

    @Test
    void testGetAllChildrenResponsible() throws Exception {
        Student student = new Student();
        student.setName("Alice");
        Page<Student> page = new PageImpl<>(
                Collections.singletonList(student),
                PageRequest.of(0, 10),
                1
        );

        when(studentService.getAllStudentsResponsible(any(), any(), eq("12345678901"))).thenReturn(page);

        mockMvc.perform(get("/api/student/my-children")
                .principal(new UsernamePasswordAuthenticationToken("12345678901", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice"));
    }
}