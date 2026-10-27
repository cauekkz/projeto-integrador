package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.stops.StudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopResponseDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.RouteStopRepository;
import br.com.vanroute.backend.repositories.SchoolStopRepository;
import br.com.vanroute.backend.repositories.StudentStopRepository;
import br.com.vanroute.backend.repositories.UserDriverContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import br.com.vanroute.backend.models.shared.enums.WeekDay;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentStopServiceTest {

    @Mock private RouteStopRepository routeStopRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private SchoolStopRepository schoolStopRepository;
    @Mock private UserDriverContractRepository userDriverContractRepository;
    @Mock private RouteService routeService;
    @Mock private Authentication authentication;

    @InjectMocks
    private StudentStopService studentStopService;

    private final StudentStopRequestDTO boarding = new StudentStopRequestDTO(null, null, StudentStopLocationType.HOME, StudentStopAction.BOARDING, null);
    private RouteStop stop;
    private Driver driver;
    private Student student;
    private School school;

    @BeforeEach
    void setUp() {
        Route route = new Route();
        route.setId(UUID.randomUUID());
        stop = new RouteStop();
        stop.setId(UUID.randomUUID());
        stop.setRoute(route);
        driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        student = new Student();
        student.setId(UUID.randomUUID());
        school = new School();
        school.setId(UUID.randomUUID());

        when(routeStopRepository.findById(stop.getId())).thenReturn(Optional.of(stop));
        when(routeService.checkOwnership(route, authentication)).thenReturn(driver);
    }

    private void activeContract() {
        UserDriverContract contract = new UserDriverContract();
        contract.setStudent(student);
        contract.setSchool(school);
        when(userDriverContractRepository.findFirstByDriverUserIdAndStudentIdAndContractStatus(
                driver.getUserId(), student.getId(), ContractStatus.ACTIVE)).thenReturn(Optional.of(contract));
    }

    private void schoolInRoute(boolean present) {
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(stop.getRoute().getId(), school.getId()))
                .thenReturn(present ? Optional.of(new SchoolStop()) : Optional.empty());
    }

    @Test
    void shouldLinkStudentToStop() {
        activeContract();
        schoolInRoute(true);
        when(studentStopRepository.save(any(StudentStop.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentStopResponseDTO response = studentStopService.createStudentStop(boarding, student.getId(), stop.getId(), authentication);

        assertEquals(stop.getId(), response.stopId());
        assertEquals(StudentStopAction.BOARDING, response.action());
    }

    @Test
    void shouldCheckOrderWithTheOtherStopsOfTheStudent() {
        activeContract();
        schoolInRoute(true);
        StudentStop alighting = new StudentStop();
        alighting.setId(UUID.randomUUID());
        when(studentStopRepository.findByStudentIdAndStopRouteId(student.getId(), stop.getRoute().getId()))
                .thenReturn(List.of(alighting));
        doThrow(new BadRequestException("ordem")).when(routeService).checkBoardingBeforeAlighting(
                argThat(stops -> stops.size() == 2 && stops.contains(alighting)));

        assertThrows(BadRequestException.class,
                () -> studentStopService.createStudentStop(boarding, student.getId(), stop.getId(), authentication));
        verify(studentStopRepository, never()).save(any());
    }

    @Test
    void shouldRejectWithoutActiveContract() {
        when(userDriverContractRepository.findFirstByDriverUserIdAndStudentIdAndContractStatus(
                driver.getUserId(), student.getId(), ContractStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> studentStopService.createStudentStop(boarding, student.getId(), stop.getId(), authentication));
        verify(studentStopRepository, never()).save(any());
    }

    @Test
    void shouldRejectRouteWithoutContractSchool() {
        activeContract();
        schoolInRoute(false);

        assertThrows(BadRequestException.class,
                () -> studentStopService.createStudentStop(boarding, student.getId(), stop.getId(), authentication));
    }

    @Test
    void shouldRejectSameActionOnOverlappingDays() {
        activeContract();
        schoolInRoute(true);
        StudentStop existing = new StudentStop();
        existing.setWeekdays(EnumSet.of(WeekDay.MONDAY, WeekDay.WEDNESDAY));
        when(studentStopRepository.findByStudentIdAndStopRouteIdAndAction(student.getId(), stop.getRoute().getId(), StudentStopAction.BOARDING))
                .thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class,
                () -> studentStopService.createStudentStop(boarding, student.getId(), stop.getId(), authentication));
        verify(studentStopRepository, never()).save(any());
    }

    private StudentStop existingStudentStop() {
        StudentStop studentStop = new StudentStop();
        studentStop.setId(UUID.randomUUID());
        studentStop.setStudent(student);
        studentStop.setStop(stop);
        when(studentStopRepository.findById(studentStop.getId())).thenReturn(Optional.of(studentStop));
        return studentStop;
    }

    @Test
    void shouldMoveStudentToAnotherStopOfTheSameRoute() {
        StudentStop studentStop = existingStudentStop();
        RouteStop other = new RouteStop();
        other.setId(UUID.randomUUID());
        other.setRoute(stop.getRoute());
        when(routeStopRepository.findById(other.getId())).thenReturn(Optional.of(other));

        studentStopService.moveStudentStop(studentStop.getId(), other.getId(), authentication);

        assertSame(other, studentStop.getStop());
    }

    @Test
    void shouldNotMoveStudentToAnotherRoute() {
        StudentStop studentStop = existingStudentStop();
        Route otherRoute = new Route();
        otherRoute.setId(UUID.randomUUID());
        RouteStop other = new RouteStop();
        other.setId(UUID.randomUUID());
        other.setRoute(otherRoute);
        when(routeStopRepository.findById(other.getId())).thenReturn(Optional.of(other));

        assertThrows(BadRequestException.class, () -> studentStopService.moveStudentStop(studentStop.getId(), other.getId(), authentication));
        assertSame(stop, studentStop.getStop());
    }

    @Test
    void shouldDeleteStudentStop() {
        StudentStop studentStop = existingStudentStop();

        studentStopService.deleteStudentStop(studentStop.getId(), authentication);

        verify(studentStopRepository).delete(studentStop);
    }

    @Test
    void shouldAllowSameActionOnOtherDays() {
        activeContract();
        schoolInRoute(true);
        StudentStop mondayWednesday = new StudentStop();
        mondayWednesday.setWeekdays(EnumSet.of(WeekDay.MONDAY, WeekDay.WEDNESDAY));
        when(studentStopRepository.findByStudentIdAndStopRouteIdAndAction(student.getId(), stop.getRoute().getId(), StudentStopAction.BOARDING))
                .thenReturn(List.of(mondayWednesday));
        when(studentStopRepository.save(any(StudentStop.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentStopRequestDTO tuesdayThursday = new StudentStopRequestDTO(null, null, StudentStopLocationType.HOME,
                StudentStopAction.BOARDING, EnumSet.of(WeekDay.TUESDAY, WeekDay.THURSDAY));
        StudentStopResponseDTO response = studentStopService.createStudentStop(tuesdayThursday, student.getId(), stop.getId(), authentication);

        assertEquals(EnumSet.of(WeekDay.TUESDAY, WeekDay.THURSDAY), response.weekdays());
    }

    @Test
    void withoutDaysMeansEveryDay() {
        activeContract();
        schoolInRoute(true);
        when(studentStopRepository.save(any(StudentStop.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentStopResponseDTO response = studentStopService.createStudentStop(boarding, student.getId(), stop.getId(), authentication);

        assertEquals(EnumSet.allOf(WeekDay.class), response.weekdays());
    }

    @Test
    void schoolLocationMustBeTheContractSchoolStop() {
        activeContract();
        schoolInRoute(true);
        when(schoolStopRepository.findByStopId(stop.getId())).thenReturn(List.of());

        StudentStopRequestDTO atSchool = new StudentStopRequestDTO(null, null, StudentStopLocationType.SCHOOL, StudentStopAction.ALIGHTING, null);

        assertThrows(BadRequestException.class,
                () -> studentStopService.createStudentStop(atSchool, student.getId(), stop.getId(), authentication));
        verify(studentStopRepository, never()).save(any());
    }

    @Test
    void schoolLocationAtTheSchoolStopIsAccepted() {
        activeContract();
        schoolInRoute(true);
        SchoolStop schoolStop = new SchoolStop();
        schoolStop.setSchool(school);
        when(schoolStopRepository.findByStopId(stop.getId())).thenReturn(List.of(schoolStop));
        when(studentStopRepository.save(any(StudentStop.class))).thenAnswer(inv -> inv.getArgument(0));

        StudentStopRequestDTO atSchool = new StudentStopRequestDTO(null, null, StudentStopLocationType.SCHOOL, StudentStopAction.ALIGHTING, null);

        assertEquals(StudentStopLocationType.SCHOOL,
                studentStopService.createStudentStop(atSchool, student.getId(), stop.getId(), authentication).locationType());
    }

    @Test
    void editingWeekdaysIgnoresItselfButNotTheOthers() {
        StudentStop editing = existingStudentStop();
        editing.setAction(StudentStopAction.BOARDING);
        editing.setWeekdays(EnumSet.of(WeekDay.MONDAY));
        StudentStop other = new StudentStop();
        other.setId(UUID.randomUUID());
        other.setWeekdays(EnumSet.of(WeekDay.FRIDAY));
        when(studentStopRepository.findByStudentIdAndStopRouteIdAndAction(student.getId(), stop.getRoute().getId(), StudentStopAction.BOARDING))
                .thenReturn(List.of(editing, other));

        // segunda -> segunda e quarta: so bate com ele mesmo, passa
        studentStopService.updateWeekdays(editing.getId(), EnumSet.of(WeekDay.MONDAY, WeekDay.WEDNESDAY), authentication);
        assertEquals(EnumSet.of(WeekDay.MONDAY, WeekDay.WEDNESDAY), editing.getWeekdays());

        // incluir sexta bate com o outro
        assertThrows(ResponseStatusException.class,
                () -> studentStopService.updateWeekdays(editing.getId(), EnumSet.of(WeekDay.FRIDAY), authentication));
    }
}
