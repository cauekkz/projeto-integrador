package br.com.vanroute.backend.controllers;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.apache.commons.lang3.ObjectUtils.Null;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import br.com.vanroute.backend.dtos.student.StudentLinkRequestDTO;
import br.com.vanroute.backend.dtos.student.studentCodeRequestDTO;
import br.com.vanroute.backend.dtos.student.AllStudentsFilterRequestDTO;
import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.services.StudentLinkService;
import br.com.vanroute.backend.services.StudentService;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import br.com.vanroute.backend.models.student.Student;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final StudentLinkService studentLinkService;

    public StudentController(StudentService studentService, StudentLinkService studentLinkService) {
        this.studentService = studentService;
        this.studentLinkService = studentLinkService;
    }

    @PostMapping("/create-student")
    public StudentResponsible createStudentWithRelation(@Valid @RequestBody StudentRequestDTO studentRequestDTO,
            Authentication authentication) {
        String cpf = authentication.getName();
        return studentService.createStudentWithRelation(studentRequestDTO, cpf);
    }

    @PostMapping("/generate-link")
    public ResponseEntity<String> generateStudentCodeToLink(
            @Valid @RequestBody StudentLinkRequestDTO studentLinkRequestDTO, Authentication authentication) {
        String cpf = authentication.getName();
        return ResponseEntity.ok(studentLinkService.generateStudentCodeToLink(studentLinkRequestDTO, cpf));
    }

    @PostMapping("/confirm-link")
    public ResponseEntity<?> createRelationWithStudent(@Valid @RequestBody studentCodeRequestDTO code,
            Authentication authentication) {
        String cpf = authentication.getName();
        studentLinkService.createStudentResponsibleRelation(code.code(), cpf);

        // dps bota aql .created() que retorna uma uri que pega os dado do fiot
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/my-children")
    public ResponseEntity<Page<Student>> getAllChildrenResponsible(@ModelAttribute AllStudentsFilterRequestDTO filter,  @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        String cpf = authentication.getName();
        return ResponseEntity.ok(studentService.getAllStudentsResponsible(filter, pageable, cpf));
    }                                                                           

}
