package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.dtos.route.RouteEditResponse;
import br.com.vanroute.backend.dtos.route.RoutePendingResponse;
import br.com.vanroute.backend.dtos.route.RouteStopResponse;
import br.com.vanroute.backend.dtos.route.UpdateRouteStopOrderRequest;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteServiceTest {

    @Mock private RouteRepository routeRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private RouteDriverRepository routeDriverRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private SchoolStopRepository schoolStopRepository;
    @Mock private Authentication authentication;
    @Mock private StudentAddressRepository studentAddressRepository;
    @Mock private UserDriverContractRepository userDriverContractRepository;

    @InjectMocks
    private RouteService routeService;

    private Route route;
    private RouteStop a, b, c, d;

    private RouteStop stop(int order) {
        RouteStop stop = new RouteStop();
        stop.setId(UUID.randomUUID());
        stop.setRoute(route);
        stop.setAddress(new Address());
        stop.setOrderIndex(order);
        return stop;
    }

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setId(UUID.randomUUID());
        a = stop(1); b = stop(2); c = stop(3); d = stop(4);

        Driver driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        driver.setUser(new User());
        when(authentication.getName()).thenReturn(driver.getUserId().toString());
        when(driverRepository.findById(driver.getUserId())).thenReturn(Optional.of(driver));
        when(routeDriverRepository.existsByRouteIdAndDriverUserId(route.getId(), driver.getUserId())).thenReturn(true);
    }

    @Test
    void swapTradesPlacesWithTheStopAtTheTarget() {
        when(routeStopRepository.findById(d.getId())).thenReturn(Optional.of(d));
        when(routeStopRepository.findFirstByRouteIdAndOrderIndex(route.getId(), 1)).thenReturn(Optional.of(a));

        routeService.updateRouteStopOrder(d.getId(), new UpdateRouteStopOrderRequest(1), authentication);

        // 4 <-> 1, o 2 e o 3 nao mudam
        assertEquals(List.of(1, 2, 3, 4), List.of(d.getOrderIndex(), b.getOrderIndex(), c.getOrderIndex(), a.getOrderIndex()));
    }

    @Test
    void swapToEmptyPositionIsRejected() {
        when(routeStopRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(routeStopRepository.findFirstByRouteIdAndOrderIndex(route.getId(), 99)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> routeService.updateRouteStopOrder(b.getId(), new UpdateRouteStopOrderRequest(99), authentication));
        assertEquals(2, b.getOrderIndex());
    }

    @Test
    void stopAtReusesTheStopOfThatAddress() {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        when(routeStopRepository.findFirstByRouteIdAndAddressId(route.getId(), address.getId())).thenReturn(Optional.of(b));

        assertSame(b, routeService.stopAt(route, address, null, null));
        verify(routeStopRepository, never()).save(any());
    }

    @Test
    void stopAtInsertsInTheMiddleAndPushesTheOthersDown() {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        when(routeStopRepository.findFirstByRouteIdAndAddressId(route.getId(), address.getId())).thenReturn(Optional.empty());
        when(routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId())).thenReturn(List.of(a, b, c, d));
        when(routeStopRepository.save(any(RouteStop.class))).thenAnswer(inv -> inv.getArgument(0));

        RouteStop created = routeService.stopAt(route, address, null, 2);

        assertEquals(2, created.getOrderIndex());
        // 1 fica; 2, 3 e 4 descem uma posicao
        assertEquals(List.of(1, 3, 4, 5), List.of(a.getOrderIndex(), b.getOrderIndex(), c.getOrderIndex(), d.getOrderIndex()));
    }

    @Test
    void stopAtWithASchoolLinksTheSchoolToTheStop() {
        School school = new School();
        school.setId(UUID.randomUUID());
        Address address = new Address();
        address.setId(UUID.randomUUID());
        when(routeStopRepository.findFirstByRouteIdAndAddressId(route.getId(), address.getId())).thenReturn(Optional.empty());
        when(routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId())).thenReturn(List.of(a, b));
        when(routeStopRepository.save(any(RouteStop.class))).thenAnswer(inv -> inv.getArgument(0));
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(route.getId(), school.getId())).thenReturn(Optional.empty());

        RouteStop created = routeService.stopAt(route, address, school, null);

        assertEquals(3, created.getOrderIndex());
        verify(schoolStopRepository).save(argThat(link -> link.getStop() == created && link.getSchool() == school));
    }

    @Test
    void secondSchoolAtTheSameAddressJoinsTheExistingStop() {
        School school = new School();
        school.setId(UUID.randomUUID());
        Address address = new Address();
        address.setId(UUID.randomUUID());
        when(routeStopRepository.findFirstByRouteIdAndAddressId(route.getId(), address.getId())).thenReturn(Optional.of(b));
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(route.getId(), school.getId())).thenReturn(Optional.empty());

        assertSame(b, routeService.stopAt(route, address, school, null));
        verify(routeStopRepository, never()).save(any());
        verify(schoolStopRepository).save(argThat(link -> link.getStop() == b && link.getSchool() == school));
    }

    @Test
    void deletingAStopClosesTheGap() {
        when(routeStopRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId())).thenReturn(List.of(a, c, d));

        routeService.deleteRouteStop(b.getId(), authentication);

        verify(routeStopRepository).delete(b);
        assertEquals(List.of(1, 2, 3), List.of(a.getOrderIndex(), c.getOrderIndex(), d.getOrderIndex()));
    }

    @Test
    void everyEditAnswersWithTheStopsAndWhatIsStillMissing() {
        when(routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId())).thenReturn(List.of(a, b));
        Student ana = new Student();
        ana.setId(UUID.randomUUID());
        ana.setName("Ana");
        StudentStop boarding = new StudentStop();
        boarding.setStudent(ana);
        boarding.setAction(StudentStopAction.BOARDING);
        boarding.setWeekdays(EnumSet.allOf(WeekDay.class));
        when(studentStopRepository.findByRouteWithStudent(route.getId())).thenReturn(List.of(boarding));

        RouteEditResponse state = routeService.routeState(route.getId());

        assertEquals(List.of(a.getId(), b.getId()), state.stops().stream().map(RouteStopResponse::id).toList());
        // ana embarca todo dia e nao desce em nenhum
        assertEquals(EnumSet.allOf(WeekDay.class), state.pending().get(0).missingAlighting());
    }

    @Test
    void stopWithStudentsCannotBeDeleted() {
        when(routeStopRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(studentStopRepository.existsByStopId(b.getId())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> routeService.deleteRouteStop(b.getId(), authentication));
        verify(routeStopRepository, never()).delete(any());
    }

    private StudentStop studentStop(Student student, StudentStopAction action, WeekDay... days) {
        StudentStop studentStop = new StudentStop();
        studentStop.setStudent(student);
        studentStop.setAction(action);
        studentStop.setWeekdays(EnumSet.copyOf(List.of(days)));
        return studentStop;
    }

    @Test
    void pendingListsDaysWithoutAlighting() {
        when(routeRepository.findById(route.getId())).thenReturn(Optional.of(route));
        Student ana = new Student();
        ana.setId(UUID.randomUUID());
        Student bia = new Student();
        bia.setId(UUID.randomUUID());
        when(studentStopRepository.findByRouteWithStudent(route.getId())).thenReturn(List.of(
                // ana embarca seg a qua mas so desembarca seg -> faltam ter e qua
                studentStop(ana, StudentStopAction.BOARDING, WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY),
                studentStop(ana, StudentStopAction.ALIGHTING, WeekDay.MONDAY),
                // bia completa
                studentStop(bia, StudentStopAction.BOARDING, WeekDay.FRIDAY),
                studentStop(bia, StudentStopAction.ALIGHTING, WeekDay.FRIDAY)));

        List<RoutePendingResponse> pending = routeService.findPending(route.getId(), authentication);

        assertEquals(1, pending.size());
        assertEquals(ana.getId(), pending.get(0).studentId());
        assertEquals(EnumSet.noneOf(WeekDay.class), pending.get(0).missingBoarding());
        assertEquals(EnumSet.of(WeekDay.TUESDAY, WeekDay.WEDNESDAY), pending.get(0).missingAlighting());
    }

    private StudentStop at(RouteStop routeStop, Student student, StudentStopAction action, WeekDay... days) {
        StudentStop studentStop = studentStop(student, action, days);
        studentStop.setStop(routeStop);
        return studentStop;
    }

    @Test
    void alightingBeforeBoardingIsRejected() {
        Student ana = new Student();
        List<StudentStop> stops = List.of(
                at(c, ana, StudentStopAction.BOARDING, WeekDay.MONDAY),
                at(a, ana, StudentStopAction.ALIGHTING, WeekDay.MONDAY));

        assertThrows(BadRequestException.class, () -> routeService.checkBoardingBeforeAlighting(stops));
    }

    @Test
    void boardingBeforeAlightingIsAccepted() {
        Student ana = new Student();
        // seg: embarca no 1 e desce no 3; embarque de sex no 4 e desembarque de sab no 2 sao dias diferentes
        routeService.checkBoardingBeforeAlighting(List.of(
                at(a, ana, StudentStopAction.BOARDING, WeekDay.MONDAY),
                at(c, ana, StudentStopAction.ALIGHTING, WeekDay.MONDAY),
                at(d, ana, StudentStopAction.BOARDING, WeekDay.FRIDAY),
                at(b, ana, StudentStopAction.ALIGHTING, WeekDay.SATURDAY)));
    }

    @Test
    void swapThatPutsAlightingBeforeBoardingIsRejected() {
        Student ana = new Student();
        ana.setId(UUID.randomUUID());
        StudentStop boarding = at(a, ana, StudentStopAction.BOARDING, WeekDay.MONDAY);
        StudentStop alighting = at(c, ana, StudentStopAction.ALIGHTING, WeekDay.MONDAY);
        when(routeStopRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(routeStopRepository.findFirstByRouteIdAndOrderIndex(route.getId(), 3)).thenReturn(Optional.of(c));
        when(studentStopRepository.findByStopId(a.getId())).thenReturn(List.of(boarding));
        when(studentStopRepository.findByStudentIdAndStopRouteId(ana.getId(), route.getId())).thenReturn(List.of(boarding, alighting));

        // embarque vai pro 3 e desembarque pro 1
        assertThrows(BadRequestException.class,
                () -> routeService.updateRouteStopOrder(a.getId(), new UpdateRouteStopOrderRequest(3), authentication));
    }
}
