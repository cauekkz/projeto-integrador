package br.com.vanroute.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vanroute.backend.dtos.student.StudentLinkRequestDTO;
import br.com.vanroute.backend.dtos.student.studentCodeRequestDTO;
import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.services.StudentLinkService;
import br.com.vanroute.backend.services.StudentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final StudentLinkService studentLinkService;

    public StudentController(StudentService studentService,StudentLinkService studentLinkService) {
        this.studentService = studentService;
        this.studentLinkService = studentLinkService;
    }

    @PostMapping("/create-student")
    public StudentResponsible createStudentWithRelation(@Valid @RequestBody StudentRequestDTO studentRequestDTO, Authentication authentication){
        String cpf = authentication.getName();
        return studentService.createStudentWithRelation(studentRequestDTO, cpf);
    }
    @PostMapping("/generate-link")
    public ResponseEntity<String> generateStudentCodeToLink(@Valid @RequestBody StudentLinkRequestDTO studentLinkRequestDTO,Authentication authentication){
        String cpf = authentication.getName();
        return ResponseEntity.ok(studentLinkService.generateStudentCodeToLink(studentLinkRequestDTO,cpf));
    }
    @PostMapping("/confirm-link")
    public ResponseEntity<?> createRelationWithStudent(@Valid @RequestBody studentCodeRequestDTO code, Authentication authentication){
        String cpf = authentication.getName();
        studentLinkService.createStudentResponsibleRelation(code.code(), cpf);
        return ResponseEntity.ok().build();
    }
    

}
