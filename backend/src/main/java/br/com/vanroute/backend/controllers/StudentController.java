package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.StudentRequestDTO;
import br.com.vanroute.backend.models.student.StudentResponsible;
import br.com.vanroute.backend.services.StudentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    public StudentResponsible createStudentWithRelation(@RequestBody StudentRequestDTO studentRequestDTO, Authentication authentication){
        String cpf = authentication.getName();
        return studentService.createStudentWithRelation(studentRequestDTO, cpf);
    }
}
