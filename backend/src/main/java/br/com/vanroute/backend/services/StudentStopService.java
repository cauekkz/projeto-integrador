package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.stops.StudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.RouteStopRepository;
import br.com.vanroute.backend.repositories.SchoolStopRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class StudentStopService {

    private final RouteStopRepository routeStopRepository;
    private final StudentStopRepository studentStopRepository;
    private final SchoolStopRepository schoolStopRepository;
    private final UserDriverContractRepository userDriverContractRepository;
    private final RouteService routeService;

    public StudentStopService(RouteStopRepository routeStopRepository, StudentStopRepository studentStopRepository,
                              SchoolStopRepository schoolStopRepository, UserDriverContractRepository userDriverContractRepository,
                              RouteService routeService) {
        this.routeStopRepository = routeStopRepository;
        this.studentStopRepository = studentStopRepository;
        this.schoolStopRepository = schoolStopRepository;
        this.userDriverContractRepository = userDriverContractRepository;
        this.routeService = routeService;
    }

    // liga o aluno a um ponto da rota. so o dono da rota, com contrato ativo com o aluno,
    // e a rota tem que passar pela escola do contrato. por dia o aluno tem no maximo um embarque e um desembarque na rota
    public StudentStopResponseDTO createStudentStop(StudentStopRequestDTO studentStopRequestDTO, UUID studentId, UUID routeStopId,
                                                    Authentication authentication) {
        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("Stop not found"));
        Driver driver = routeService.checkOwnership(routeStop.getRoute(), authentication);
        UUID routeId = routeStop.getRoute().getId();

        UserDriverContract contract = userDriverContractRepository
                .findFirstByDriverUserIdAndStudentIdAndContractStatus(driver.getUserId(), studentId, ContractStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Não existe contrato ativo com esse aluno"));

        if (schoolStopRepository.findFirstByStopRouteIdAndSchoolId(routeId, contract.getSchool().getId()).isEmpty()) {
            throw new BadRequestException("A rota não passa pela escola do contrato");
        }
        checkSchoolLocation(studentStopRequestDTO.locationType(), routeStopId, contract.getSchool().getId());
        if (studentStopRepository.existsByStudentIdAndStopId(studentId, routeStopId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student is already registered at this stop.");
        }
        Set<WeekDay> weekdays = normalize(studentStopRequestDTO.weekdays());
        checkOverlap(studentId, routeId, studentStopRequestDTO.action(), weekdays, null);

        StudentStop studentStop = new StudentStop();
        studentStop.setLocationType(studentStopRequestDTO.locationType());
        studentStop.setAction(studentStopRequestDTO.action());
        studentStop.setWeekdays(weekdays);
        studentStop.setStop(routeStop);
        studentStop.setStudent(contract.getStudent());
        checkOrder(studentStop, routeId);
        return toResponse(studentStopRepository.save(studentStop));
    }

    // muda o aluno de ponto dentro da mesma rota (pra trocar de rota: exclui e cria de novo)
    @Transactional
    public StudentStopResponseDTO moveStudentStop(UUID studentStopId, UUID routeStopId, Authentication authentication) {
        StudentStop studentStop = studentStopRepository.findById(studentStopId)
                .orElseThrow(() -> new EntityNotFoundException("Student stop not found"));
        Driver driver = routeService.checkOwnership(studentStop.getStop().getRoute(), authentication);
        RouteStop target = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("Stop not found"));
        if (!target.getRoute().getId().equals(studentStop.getStop().getRoute().getId())) {
            throw new BadRequestException("O ponto precisa ser da mesma rota");
        }
        if (studentStop.getLocationType() == StudentStopLocationType.SCHOOL) {
            UserDriverContract contract = userDriverContractRepository
                    .findFirstByDriverUserIdAndStudentIdAndContractStatus(driver.getUserId(), studentStop.getStudent().getId(), ContractStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException("Não existe contrato ativo com esse aluno"));
            checkSchoolLocation(StudentStopLocationType.SCHOOL, routeStopId, contract.getSchool().getId());
        } else if (studentStop.getLocationType() != null) {
            checkSchoolLocation(studentStop.getLocationType(), routeStopId, null);
        }
        if (studentStopRepository.existsByStudentIdAndStopId(studentStop.getStudent().getId(), routeStopId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student is already registered at this stop.");
        }
        studentStop.setStop(target);
        checkOrder(studentStop, target.getRoute().getId());
        return toResponse(studentStop);
    }

    // troca os dias desse student stop; os outros do aluno na rota nao mudam
    @Transactional
    public StudentStopResponseDTO updateWeekdays(UUID studentStopId, Set<WeekDay> newWeekdays, Authentication authentication) {
        StudentStop studentStop = findOwned(studentStopId, authentication);
        Set<WeekDay> weekdays = normalize(newWeekdays);
        checkOverlap(studentStop.getStudent().getId(), studentStop.getStop().getRoute().getId(),
                studentStop.getAction(), weekdays, studentStopId);
        studentStop.setWeekdays(weekdays);
        checkOrder(studentStop, studentStop.getStop().getRoute().getId());
        return toResponse(studentStop);
    }

    // os pontos do aluno na rota ja com a mudanca aplicada; embarque tem que vir antes do desembarque
    private void checkOrder(StudentStop changed, UUID routeId) {
        List<StudentStop> stops = new ArrayList<>(studentStopRepository
                .findByStudentIdAndStopRouteId(changed.getStudent().getId(), routeId).stream()
                .filter(existing -> changed.getId() == null || !changed.getId().equals(existing.getId()))
                .toList());
        stops.add(changed);
        routeService.checkBoardingBeforeAlighting(stops);
    }

    // sem dias = todos os dias
    private Set<WeekDay> normalize(Set<WeekDay> weekdays) {
        return weekdays == null || weekdays.isEmpty() ? EnumSet.allOf(WeekDay.class) : EnumSet.copyOf(weekdays);
    }

    // o aluno pode ter varios embarques (ou desembarques) na rota, desde que em dias diferentes
    private void checkOverlap(UUID studentId, UUID routeId, StudentStopAction action, Set<WeekDay> weekdays, UUID ignoreId) {
        boolean overlaps = studentStopRepository.findByStudentIdAndStopRouteIdAndAction(studentId, routeId, action).stream()
                .filter(existing -> ignoreId == null || !ignoreId.equals(existing.getId()))
                .anyMatch(existing -> !Collections.disjoint(existing.getWeekdays(), weekdays));
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno já tem essa ação nessa rota em algum desses dias");
        }
    }

    public void deleteStudentStop(UUID studentStopId, Authentication authentication) {
        studentStopRepository.delete(findOwned(studentStopId, authentication));
    }

    public static StudentStopResponseDTO toResponse(StudentStop studentStop) {
        return new StudentStopResponseDTO(
                studentStop.getId(),
                studentStop.getStudent().getId(),
                studentStop.getStudent().getName(),
                studentStop.getStop().getId(),
                studentStop.getLocationType(),
                studentStop.getAction(),
                studentStop.getWeekdays()
        );
    }

    // SCHOOL tem que ser no ponto da escola do contrato; HOME/OTHER nao pode ser em ponto que tem escola
    private void checkSchoolLocation(StudentStopLocationType locationType, UUID routeStopId, UUID schoolId) {
        List<SchoolStop> schoolStops = schoolStopRepository.findByStopId(routeStopId);
        if (locationType == StudentStopLocationType.SCHOOL) {
            if (schoolStops.stream().noneMatch(schoolStop -> schoolStop.getSchool().getId().equals(schoolId))) {
                throw new BadRequestException("Ponto SCHOOL precisa ser o ponto da escola do contrato");
            }
        } else if (!schoolStops.isEmpty()) {
            throw new BadRequestException("Ponto de escola só aceita aluno marcado como SCHOOL");
        }
    }

    private StudentStop findOwned(UUID studentStopId, Authentication authentication) {
        StudentStop studentStop = studentStopRepository.findById(studentStopId)
                .orElseThrow(() -> new EntityNotFoundException("Student stop not found"));
        routeService.checkOwnership(studentStop.getStop().getRoute(), authentication);
        return studentStop;
    }
}
