package br.com.vanroute.backend.controllers;
import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.student.StudentResponsibleResponseDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import br.com.vanroute.backend.dtos.route.StudentRouteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import br.com.vanroute.backend.dtos.student.StudentLinkRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentCodeRequestDTO;
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
    public StudentResponsibleResponseDTO createStudentWithRelation(@Valid @RequestBody StudentRequestDTO studentRequestDTO,
                                                                   Authentication authentication) {
        return studentService.createStudentWithRelation(studentRequestDTO, AuthenticatedUser.id(authentication));
    }

    @PostMapping("/generate-link")
    public ResponseEntity<String> generateStudentCodeToLink(
            @Valid @RequestBody StudentLinkRequestDTO studentLinkRequestDTO, Authentication authentication) {
        return ResponseEntity.ok(studentLinkService.generateStudentCodeToLink(studentLinkRequestDTO, AuthenticatedUser.id(authentication)));
    }

    @PostMapping("/confirm-link")
    public ResponseEntity<Void> createRelationWithStudent(@Valid @RequestBody StudentCodeRequestDTO code,
            Authentication authentication) {
        studentLinkService.createStudentResponsibleRelation(code.code(), AuthenticatedUser.id(authentication));

        // TODO: usar .created(uri) apontando pro recurso criado
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/{studentId}/routes")
    public ResponseEntity<List<StudentRouteResponse>> getStudentRoutes(@PathVariable UUID studentId, Authentication authentication) {
        return ResponseEntity.ok(studentService.getStudentRoutes(studentId, AuthenticatedUser.id(authentication)));
    }

    @GetMapping("/my-children")
    public ResponseEntity<Page<Student>> getAllChildrenResponsible(@ModelAttribute AllStudentsFilterRequestDTO filter,  @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        return ResponseEntity.ok(studentService.getAllStudentsResponsible(filter, pageable, AuthenticatedUser.id(authentication)));
    }                                                                           

}
