package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.route.stops.StudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopResponseDTO;
import br.com.vanroute.backend.services.StudentStopService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/student-stop")
public class StudentStopController {

    private final StudentStopService studentStopService;

    public StudentStopController(StudentStopService studentStopService) {
        this.studentStopService = studentStopService;
    }
    @PostMapping("/{studentId}/{routeStopId}")
    public StudentStopResponseDTO createStudentStop(@RequestBody StudentStopRequestDTO studentStopRequestDTO,
                                                    @PathVariable UUID studentId, @PathVariable UUID routeStopId) {
        return studentStopService.createStudentStop(studentStopRequestDTO, studentId, routeStopId);
    }

}

