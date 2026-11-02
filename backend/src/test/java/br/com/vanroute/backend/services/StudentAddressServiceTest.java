package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteDriver;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentAddress;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentAddressServiceTest {

    @Mock private StudentAddressRepository studentAddressRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private ResponsibleRepository responsibleRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private RouteDriverRepository routeDriverRepository;
    @Mock private TripRepository tripRepository;
    @Mock private AddressService addressService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private StudentAddressService service;

    private final UUID parent = UUID.randomUUID();
    private Student ana;
    private Address siblingHome;

    @BeforeEach
    void setUp() {
        ana = new Student();
        ana.setId(UUID.randomUUID());
        ana.setName("Ana");
        siblingHome = new Address();
        siblingHome.setId(UUID.randomUUID());
        when(studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(parent, ana.getId())).thenReturn(true);
        when(responsibleRepository.findById(parent)).thenReturn(Optional.of(new Responsible()));
        when(addressRepository.findById(siblingHome.getId())).thenReturn(Optional.of(siblingHome));
    }

    @Test
    void reusingTheSiblingsAddressDoesNotCallTheMapApi() {
        when(studentAddressRepository.isAddressOfResponsibleChildren(parent, siblingHome.getId())).thenReturn(true);

        Address address = service.resolve(null, siblingHome.getId(), parent);

        assertSame(siblingHome, address);
        verifyNoInteractions(addressService);
    }

    @Test
    void cannotReuseSomeoneElsesAddress() {
        when(studentAddressRepository.isAddressOfResponsibleChildren(parent, siblingHome.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.resolve(null, siblingHome.getId(), parent));
    }

    @Test
    void billingAddressOfTheParentCanBeReused() {
        Responsible responsible = new Responsible();
        responsible.setAddress(siblingHome);
        when(responsibleRepository.findById(parent)).thenReturn(Optional.of(responsible));

        assertSame(siblingHome, service.resolve(null, siblingHome.getId(), parent));
    }

    @Test
    void needsANewAddressOrAnExistingOneNotBoth() {
        AddressRequestDTO newAddress = new AddressRequestDTO("Rua", "01000-000", "SP", "Centro", 1, "SP", null, null);

        assertThrows(BadRequestException.class, () -> service.resolve(null, null, parent));
        assertThrows(BadRequestException.class, () -> service.resolve(newAddress, siblingHome.getId(), parent));
    }

    private StudentAddress address(String label) {
        StudentAddress sa = new StudentAddress();
        sa.setId(UUID.randomUUID());
        sa.setStudent(ana);
        sa.setAddress(siblingHome);
        sa.setLabel(label);
        when(studentAddressRepository.findByIdAndStudentId(sa.getId(), ana.getId())).thenReturn(Optional.of(sa));
        return sa;
    }

    @Test
    void removingDeletesOnlyTheLinkTakesTheStudentOutOfThoseStopsAndWarnsTheDriver() {
        StudentAddress grandma = address("Casa da avó");
        when(studentAddressRepository.countByStudentId(ana.getId())).thenReturn(2L);
        Route route = new Route();
        route.setId(UUID.randomUUID());
        route.setName("Ida");
        RouteStop stop = new RouteStop();
        stop.setRoute(route);
        stop.setOrderIndex(2);
        StudentStop boarding = new StudentStop();
        boarding.setStop(stop);
        boarding.setLocationType(StudentStopLocationType.HOME);
        when(studentStopRepository.findByStudentIdAndStopAddressId(ana.getId(), siblingHome.getId())).thenReturn(List.of(boarding));
        Driver driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        RouteDriver routeDriver = new RouteDriver();
        routeDriver.setDriver(driver);
        when(routeDriverRepository.findByRouteIdAndEndDateIsNull(route.getId())).thenReturn(List.of(routeDriver));
        when(tripRepository.findFirstByRouteIdAndDateAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());

        service.remove(ana.getId(), grandma.getId(), parent);

        // apaga so a ligacao (o endereco continua pra quem mais usa)
        verify(studentAddressRepository).delete(grandma);
        verify(studentStopRepository).deleteAll(List.of(boarding));
        verify(notificationService).send(eq(List.of(driver.getUserId())), eq(NotificationType.ROUTE), eq("Endereço removido"),
                contains("Casa da avó"), anyMap());
    }

    @Test
    void theLastAddressCannotBeRemoved() {
        StudentAddress home = address("Casa");
        when(studentAddressRepository.countByStudentId(ana.getId())).thenReturn(1L);

        assertThrows(ResponseStatusException.class, () -> service.remove(ana.getId(), home.getId(), parent));
        verify(studentAddressRepository, never()).delete(any());
    }

    @Test
    void notWhileATripIsRunningWithThatAddress() {
        StudentAddress grandma = address("Casa da avó");
        when(studentAddressRepository.countByStudentId(ana.getId())).thenReturn(2L);
        Route route = new Route();
        route.setId(UUID.randomUUID());
        RouteStop stop = new RouteStop();
        stop.setRoute(route);
        StudentStop boarding = new StudentStop();
        boarding.setStop(stop);
        boarding.setLocationType(StudentStopLocationType.HOME);
        when(studentStopRepository.findByStudentIdAndStopAddressId(ana.getId(), siblingHome.getId())).thenReturn(List.of(boarding));
        when(tripRepository.findFirstByRouteIdAndDateAndStatusIn(eq(route.getId()), any(), any())).thenReturn(Optional.of(new Trip()));

        assertThrows(ResponseStatusException.class, () -> service.remove(ana.getId(), grandma.getId(), parent));
        verify(studentAddressRepository, never()).delete(any());
        verify(studentStopRepository, never()).deleteAll(any());
    }
}
