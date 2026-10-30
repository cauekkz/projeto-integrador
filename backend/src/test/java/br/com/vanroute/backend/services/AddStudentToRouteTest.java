package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.stops.AddStudentToRouteRequest;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.contract.UserDriverContract;
import br.com.vanroute.backend.models.contract.enums.ContractStatus;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.school.School;
import br.com.vanroute.backend.models.school.SchoolStop;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentAddress;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.user.Driver;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// "adicionar aluno na rota" num passo so (StudentStopService.addStudentToRoute)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddStudentToRouteTest {

    @Mock private RouteStopRepository routeStopRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private SchoolStopRepository schoolStopRepository;
    @Mock private UserDriverContractRepository userDriverContractRepository;
    @Mock private RouteService routeService;
    @Mock private StudentAddressRepository studentAddressRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private StudentStopService service;

    private Route route;
    private Student ana;
    private School school;
    private StudentAddress home;
    private RouteStop homeStop;
    private RouteStop schoolStop;

    private static Address address() {
        Address a = new Address();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static RouteStop stop(Route route, Address address, int order) {
        RouteStop stop = new RouteStop();
        stop.setId(UUID.randomUUID());
        stop.setRoute(route);
        stop.setAddress(address);
        stop.setOrderIndex(order);
        return stop;
    }

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setId(UUID.randomUUID());
        Driver driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        ana = new Student();
        ana.setId(UUID.randomUUID());
        school = new School();
        school.setId(UUID.randomUUID());
        school.setAddress(address());
        home = new StudentAddress();
        home.setId(UUID.randomUUID());
        home.setStudent(ana);
        home.setAddress(address());
        homeStop = stop(route, home.getAddress(), 1);
        schoolStop = stop(route, school.getAddress(), 2);

        UserDriverContract contract = new UserDriverContract();
        contract.setStudent(ana);
        contract.setSchool(school);
        when(routeRepository.findById(route.getId())).thenReturn(Optional.of(route));
        when(routeService.checkOwnership(route, authentication)).thenReturn(driver);
        when(userDriverContractRepository.findFirstByDriverUserIdAndStudentIdAndContractStatus(driver.getUserId(), ana.getId(), ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));
        when(studentAddressRepository.findByIdAndStudentId(home.getId(), ana.getId())).thenReturn(Optional.of(home));
        when(routeService.stopAt(eq(route), eq(home.getAddress()), isNull(), any())).thenReturn(homeStop);
        when(routeService.stopAt(eq(route), eq(school.getAddress()), eq(school), any())).thenReturn(schoolStop);

        // o resto das regras do createStudentStop (o ponto existe, e endereco do aluno, a escola esta na rota)
        when(routeStopRepository.findById(homeStop.getId())).thenReturn(Optional.of(homeStop));
        when(routeStopRepository.findById(schoolStop.getId())).thenReturn(Optional.of(schoolStop));
        when(studentAddressRepository.existsByStudentIdAndAddressId(ana.getId(), home.getAddress().getId())).thenReturn(true);
        SchoolStop link = new SchoolStop();
        link.setSchool(school);
        link.setStop(schoolStop);
        when(schoolStopRepository.findByStopId(schoolStop.getId())).thenReturn(List.of(link));
        when(studentStopRepository.save(any(StudentStop.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void schoolAlreadyInRoute(boolean present) {
        SchoolStop link = new SchoolStop();
        link.setSchool(school);
        link.setStop(schoolStop);
        // sem a escola: a 1a consulta vem vazia e, depois que o stopAt cria o ponto da escola, ela aparece
        when(schoolStopRepository.findFirstByStopRouteIdAndSchoolId(route.getId(), school.getId()))
                .thenReturn(present ? Optional.of(link) : Optional.empty(), Optional.of(link));
    }

    private AddStudentToRouteRequest at(StudentStopAction action, StudentStopLocationType type, UUID studentAddressId) {
        return new AddStudentToRouteRequest(action, type, studentAddressId, null, null);
    }

    @Test
    void homeCreatesOnlyTheHomeStopNotTheSchool() {
        schoolAlreadyInRoute(false);

        service.addStudentToRoute(route.getId(), ana.getId(), at(StudentStopAction.BOARDING, StudentStopLocationType.HOME, home.getId()), authentication);

        verify(routeService).stopAt(route, home.getAddress(), null, null);
        verify(routeService, never()).stopAt(eq(route), eq(school.getAddress()), any(), any());
        verify(studentStopRepository).save(argThat(ss -> ss.getStop() == homeStop && ss.getAction() == StudentStopAction.BOARDING));
    }

    @Test
    void schoolIsCreatedWhenTheStudentGoesThereAndItIsNotInTheRoute() {
        schoolAlreadyInRoute(false);

        service.addStudentToRoute(route.getId(), ana.getId(), at(StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, null), authentication);

        // desembarque novo vai pro fim
        verify(routeService).stopAt(route, school.getAddress(), school, null);
        verify(studentStopRepository).save(argThat(ss -> ss.getStop() == schoolStop && ss.getLocationType() == StudentStopLocationType.SCHOOL));
    }

    @Test
    void boardingAtANewSchoolGoesBeforeWhereTheStudentAlights() {
        // volta: ana ja desce em casa no ponto 1; embarcar na escola cria a escola no 1 (a casa desce pro 2)
        schoolAlreadyInRoute(false);
        StudentStop alighting = new StudentStop();
        alighting.setStop(stop(route, home.getAddress(), 1));
        when(studentStopRepository.findByStudentIdAndStopRouteIdAndAction(ana.getId(), route.getId(), StudentStopAction.ALIGHTING))
                .thenReturn(List.of(alighting));

        service.addStudentToRoute(route.getId(), ana.getId(), at(StudentStopAction.BOARDING, StudentStopLocationType.SCHOOL, null), authentication);

        verify(routeService).stopAt(route, school.getAddress(), school, 1);
    }

    @Test
    void newBoardingGoesBeforeTheAlightingTheStudentAlreadyHas() {
        schoolAlreadyInRoute(true);
        StudentStop alighting = new StudentStop();
        alighting.setStop(stop(route, address(), 3));
        when(studentStopRepository.findByStudentIdAndStopRouteIdAndAction(ana.getId(), route.getId(), StudentStopAction.ALIGHTING))
                .thenReturn(List.of(alighting));

        service.addStudentToRoute(route.getId(), ana.getId(), at(StudentStopAction.BOARDING, StudentStopLocationType.HOME, home.getId()), authentication);

        // desce no 3 e a escola esta no 2: entra no 2 (o menor dos dois)
        verify(routeService).stopAt(route, home.getAddress(), null, 2);
        verify(routeService, never()).stopAt(eq(route), eq(school.getAddress()), any(), any());
    }

    @Test
    void secondHomeOfAMorningRouteGoesBeforeTheSchoolThatAlreadyExists() {
        // ida ja com casa 1 e escola 2: a casa de outro aluno entra no 2 (antes da escola), nao no fim
        schoolAlreadyInRoute(true);

        service.addStudentToRoute(route.getId(), ana.getId(), at(StudentStopAction.BOARDING, StudentStopLocationType.HOME, home.getId()), authentication);

        verify(routeService).stopAt(route, home.getAddress(), null, schoolStop.getOrderIndex());
    }

    @Test
    void theDriverCanChooseThePositionOfANewStop() {
        schoolAlreadyInRoute(true);

        service.addStudentToRoute(route.getId(), ana.getId(),
                new AddStudentToRouteRequest(StudentStopAction.BOARDING, StudentStopLocationType.HOME, home.getId(), null, 1), authentication);

        verify(routeService).stopAt(route, home.getAddress(), null, 1);
    }

    @Test
    void atSchoolUsesTheStopOfTheContractSchool() {
        schoolAlreadyInRoute(true);

        service.addStudentToRoute(route.getId(), ana.getId(), at(StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, null), authentication);

        verify(routeService, never()).stopAt(any(), any(), any(), any());
        verify(studentStopRepository).save(argThat(ss -> ss.getStop() == schoolStop && ss.getLocationType() == StudentStopLocationType.SCHOOL));
    }

    @Test
    void wrongChoicesAreRefused() {
        schoolAlreadyInRoute(true);
        assertThrows(BadRequestException.class, () -> service.addStudentToRoute(route.getId(), ana.getId(),
                at(StudentStopAction.ALIGHTING, StudentStopLocationType.SCHOOL, home.getId()), authentication));
        assertThrows(BadRequestException.class, () -> service.addStudentToRoute(route.getId(), ana.getId(),
                at(StudentStopAction.BOARDING, StudentStopLocationType.HOME, null), authentication));
        // endereco de outro aluno
        assertThrows(BadRequestException.class, () -> service.addStudentToRoute(route.getId(), ana.getId(),
                at(StudentStopAction.BOARDING, StudentStopLocationType.HOME, UUID.randomUUID()), authentication));
        verify(studentStopRepository, never()).save(any());
    }
}
