package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.stops.SchoolStopResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.DriverSchoolsRepository;
import br.com.vanroute.backend.repositories.RouteStopRepository;
import br.com.vanroute.backend.repositories.SchoolRepository;
import br.com.vanroute.backend.repositories.SchoolStopRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.UUID;

@Service
public class SchoolStopService {

    private final SchoolStopRepository schoolStopRepository;
    private final SchoolRepository schoolRepository;
    private final RouteStopRepository routeStopRepository;
    private final DriverSchoolsRepository driverSchoolsRepository;
    private final RouteService routeService;
    private final StudentStopRepository studentStopRepository;

    public SchoolStopService(SchoolStopRepository schoolStopRepository, SchoolRepository schoolRepository, RouteStopRepository routeStopRepository,
                             DriverSchoolsRepository driverSchoolsRepository, RouteService routeService,
                             StudentStopRepository studentStopRepository) {
        this.schoolStopRepository = schoolStopRepository;
        this.schoolRepository = schoolRepository;
        this.routeStopRepository = routeStopRepository;
        this.driverSchoolsRepository = driverSchoolsRepository;
        this.routeService = routeService;
        this.studentStopRepository = studentStopRepository;
    }

    // liga a escola a um ponto da rota; so o dono da rota e com uma escola dele
    public SchoolStopResponseDTO addSchoolStop(UUID schoolId, UUID routeStopId, Authentication authentication){
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found."));
        RouteStop route = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("Route stop not found"));
        Driver driver = routeService.checkOwnership(route.getRoute(), authentication);

        if (!driverSchoolsRepository.existsByDriverUserIdAndSchoolId(driver.getUserId(), schoolId)) {
            throw new AccessDeniedException("Motorista não possui essa escola");
        }
        if (schoolStopRepository.findFirstByStopRouteIdAndSchoolId(route.getRoute().getId(), schoolId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa escola já está nessa rota");
        }
        if (studentStopRepository.existsByStopIdAndLocationTypeIn(routeStopId,
                EnumSet.of(StudentStopLocationType.HOME, StudentStopLocationType.OTHER))) {
            throw new BadRequestException("Esse ponto tem aluno marcado como HOME/OTHER, não pode virar ponto de escola");
        }

        SchoolStop schoolStop = new SchoolStop();

        schoolStop.setSchool(school);
        schoolStop.setStop(route);
        SchoolStop saved = schoolStopRepository.save(schoolStop);
        return new SchoolStopResponseDTO(saved.getId(), schoolId, routeStopId);
    }

    // nao deixa tirar a escola da rota se algum aluno da rota tem contrato com ela
    public void deleteSchoolStop(UUID schoolStopId, Authentication authentication) {
        SchoolStop schoolStop = schoolStopRepository.findById(schoolStopId)
                .orElseThrow(() -> new EntityNotFoundException("School stop not found"));
        UUID routeId = schoolStop.getStop().getRoute().getId();
        routeService.checkOwnership(schoolStop.getStop().getRoute(), authentication);
        if (studentStopRepository.existsStudentUsingSchoolInRoute(routeId, schoolStop.getSchool().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existem alunos nessa rota com contrato com essa escola");
        }
        schoolStopRepository.delete(schoolStop);
    }
}
