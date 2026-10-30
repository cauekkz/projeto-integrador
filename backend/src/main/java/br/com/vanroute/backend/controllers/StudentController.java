package br.com.vanroute.backend.controllers;

import org.springframework.web.bind.annotation.DeleteMapping;
import br.com.vanroute.backend.services.TripService;
import br.com.vanroute.backend.services.StudentAbsenceService;
import br.com.vanroute.backend.dtos.trip.StudentTripTodayDTO;
import br.com.vanroute.backend.dtos.trip.AbsenceResponseDTO;
import br.com.vanroute.backend.dtos.trip.AbsenceRequestDTO;import br.com.vanroute.backend.config.AuthenticatedUser;
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
import br.com.vanroute.backend.services.StudentLinkService;
import br.com.vanroute.backend.services.StudentService;
import br.com.vanroute.backend.services.StudentAddressService;
import br.com.vanroute.backend.dtos.student.StudentAddressRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentPlaceResponseDTO;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import br.com.vanroute.backend.models.student.Student;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final StudentLinkService studentLinkService;
    private final StudentAbsenceService studentAbsenceService;
    private final TripService tripService;
    private final StudentAddressService studentAddressService;

    public StudentController(StudentService studentService, StudentLinkService studentLinkService,
                             StudentAbsenceService studentAbsenceService, TripService tripService,
                             StudentAddressService studentAddressService) {
        this.studentService = studentService;
        this.studentLinkService = studentLinkService;
        this.studentAbsenceService = studentAbsenceService;
        this.tripService = tripService;
        this.studentAddressService = studentAddressService;
    }

    // enderecos onde o aluno e buscado/deixado (o motorista escolhe entre eles ao criar o ponto da rota)
    @GetMapping("/{studentId}/addresses")
    public List<StudentPlaceResponseDTO> addresses(@PathVariable UUID studentId, Authentication authentication) {
        return studentAddressService.list(studentId, AuthenticatedUser.id(authentication));
    }

    // novo endereco (address) ou reaproveitado (addressId: de um irmao ou o de cobranca do responsavel)
    @PostMapping("/{studentId}/addresses")
    public ResponseEntity<StudentPlaceResponseDTO> addAddress(@PathVariable UUID studentId, @Valid @RequestBody StudentAddressRequestDTO request,
                                                              Authentication authentication) {
        return ResponseEntity.status(201).body(studentAddressService.add(studentId, request, AuthenticatedUser.id(authentication)));
    }

    // tira o endereco do aluno (apaga so a ligacao): ele sai dos pontos de casa com esse endereco e o motorista e avisado
    @DeleteMapping("/{studentId}/addresses/{studentAddressId}")
    public ResponseEntity<Void> removeAddress(@PathVariable UUID studentId, @PathVariable UUID studentAddressId, Authentication authentication) {
        studentAddressService.remove(studentId, studentAddressId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
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

    // responsavel avisa que o filho nao vai numa rota num dia (sai da trip e o motorista e avisado no chat)
    @PostMapping("/{studentId}/absences")
    public ResponseEntity<AbsenceResponseDTO> createAbsence(@PathVariable UUID studentId, @Valid @RequestBody AbsenceRequestDTO request,
                                                            Authentication authentication) {
        return ResponseEntity.status(201).body(studentAbsenceService.create(studentId, request, AuthenticatedUser.id(authentication)));
    }

    @GetMapping("/{studentId}/absences")
    public ResponseEntity<List<AbsenceResponseDTO>> upcomingAbsences(@PathVariable UUID studentId, Authentication authentication) {
        return ResponseEntity.ok(studentAbsenceService.upcoming(studentId, AuthenticatedUser.id(authentication)));
    }

    @DeleteMapping("/{studentId}/absences/{absenceId}")
    public ResponseEntity<Void> cancelAbsence(@PathVariable UUID studentId, @PathVariable UUID absenceId, Authentication authentication) {
        studentAbsenceService.cancel(studentId, absenceId, AuthenticatedUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    // trips de hoje do filho (com o tripId o app abre o WebSocket /trip/{tripId})
    @GetMapping("/{studentId}/today-trips")
    public ResponseEntity<List<StudentTripTodayDTO>> todayTrips(@PathVariable UUID studentId, Authentication authentication) {
        return ResponseEntity.ok(tripService.studentTripsToday(studentId, AuthenticatedUser.id(authentication)));
    }

    @GetMapping("/my-children")
    public ResponseEntity<Page<Student>> getAllChildrenResponsible(@ModelAttribute AllStudentsFilterRequestDTO filter,  @PageableDefault(size = 10) Pageable pageable, Authentication authentication) {
        return ResponseEntity.ok(studentService.getAllStudentsResponsible(filter, pageable, AuthenticatedUser.id(authentication)));
    }                                                                           

}
