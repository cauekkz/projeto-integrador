package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.stops.StudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopResponseDTO;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.repositories.RouteStopRepository;
import br.com.vanroute.backend.repositories.StudentRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StudentStopService {

    private final StudentRepository studentRepository;
    private final RouteStopRepository routeStopRepository;
    private final StudentStopRepository studentStopRepository;

    public StudentStopService(StudentRepository studentRepository, RouteStopRepository routeStopRepository, StudentStopRepository studentStopRepository) {
        this.studentRepository = studentRepository;
        this.routeStopRepository = routeStopRepository;
        this.studentStopRepository = studentStopRepository;
    }

    public StudentStopResponseDTO createStudentStop(StudentStopRequestDTO studentStopRequestDTO, UUID studentId, UUID routeStopId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new RuntimeException("Stop not found"));

        boolean alreadyExists = studentStopRepository.existsByStudentIdAndStopId(studentId, routeStopId);
        if(alreadyExists){
            throw new RuntimeException("Student is already registered at this stop.");
        }
        StudentStop studentStop = new StudentStop();
        studentStop.setLocationType(studentStopRequestDTO.locationType());
        studentStop.setAction(studentStopRequestDTO.action());
        studentStop.setStop(routeStop);
        studentStop.setStudent(student);
        StudentStop saved = studentStopRepository.save(studentStop);
        return new StudentStopResponseDTO(
                saved.getId(),
                saved.getStudent().getId(),
                saved.getStop().getId(),
                saved.getLocationType(),
                saved.getAction()
        );
    }
    
}
