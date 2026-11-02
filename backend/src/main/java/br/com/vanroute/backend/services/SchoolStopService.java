package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.RouteEditResponse;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.repositories.SchoolStopRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SchoolStopService {

    private final SchoolStopRepository schoolStopRepository;
    private final RouteService routeService;
    private final StudentStopRepository studentStopRepository;

    // a escola entra na rota sozinha, no add-student (a do contrato do aluno)
    public SchoolStopService(SchoolStopRepository schoolStopRepository, RouteService routeService,
                             StudentStopRepository studentStopRepository) {
        this.schoolStopRepository = schoolStopRepository;
        this.routeService = routeService;
        this.studentStopRepository = studentStopRepository;
    }

    // nao deixa tirar a escola do ponto se algum aluno dela embarca ou desce ali (student stop SCHOOL)
    @Transactional
    public RouteEditResponse deleteSchoolStop(UUID schoolStopId, Authentication authentication) {
        SchoolStop schoolStop = schoolStopRepository.findById(schoolStopId)
                .orElseThrow(() -> new EntityNotFoundException("School stop not found"));
        UUID routeId = schoolStop.getStop().getRoute().getId();
        routeService.checkOwnership(schoolStop.getStop().getRoute(), authentication);
        if (studentStopRepository.existsSchoolStudentAtStop(schoolStop.getStop().getId(), schoolStop.getSchool().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tem aluno dessa escola embarcando ou descendo nesse ponto");
        }
        schoolStopRepository.delete(schoolStop);
        return routeService.routeState(routeId);
    }
}
