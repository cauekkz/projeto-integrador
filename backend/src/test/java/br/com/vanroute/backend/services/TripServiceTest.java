package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.trip.TripLiveStateDTO;
import br.com.vanroute.backend.dtos.trip.TripLiveStopDTO;
import br.com.vanroute.backend.dtos.trip.TripStudentDTO;
import org.springframework.test.util.ReflectionTestUtils;
import br.com.vanroute.backend.dtos.route.RoutePendingResponse;
import br.com.vanroute.backend.dtos.trip.TripResponseDTO;
import br.com.vanroute.backend.dtos.trip.TripStartRequestDTO;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.StudentAbsence;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.vehicle.DriverVehicle;
import br.com.vanroute.backend.models.vehicle.Vehicle;
import br.com.vanroute.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private BoardingRepository boardingRepository;
    @Mock private StudentAbsenceRepository studentAbsenceRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private RouteDriverRepository routeDriverRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private DriverVehicleRepository driverVehicleRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;
    @Mock private RouteService routeService;
    @Mock private TripLiveService tripLiveService;
    @Mock private TripNotificationService tripNotificationService;

    @InjectMocks
    private TripService tripService;

    private final LocalDate today = LocalDate.now(TripService.ZONE);
    private final WeekDay todayWeekDay = WeekDay.valueOf(today.getDayOfWeek().name());
    private final WeekDay otherWeekDay = WeekDay.values()[(todayWeekDay.ordinal() + 1) % 7];
    private Route route;
    private Driver driver;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setId(UUID.randomUUID());
        route.setName("Ida");
        driver = new Driver();
        driver.setUserId(UUID.randomUUID());
        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());

        when(routeRepository.findById(route.getId())).thenReturn(Optional.of(route));
        when(driverRepository.findById(driver.getUserId())).thenReturn(Optional.of(driver));
        when(routeDriverRepository.existsByRouteIdAndDriverUserId(route.getId(), driver.getUserId())).thenReturn(true);
        when(routeService.pendingOf(route.getId())).thenReturn(List.of());
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), today, TripStatus.CANCELED)).thenReturn(Optional.empty());
        when(driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driver.getUserId(), vehicle.getId()))
                .thenReturn(Optional.of(new DriverVehicle()));
        when(vehicleRepository.getReferenceById(vehicle.getId())).thenReturn(vehicle);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
    }

    private Student student(String name) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private StudentStop studentStop(Student student, RouteStop stop, StudentStopAction action, WeekDay... days) {
        StudentStop ss = new StudentStop();
        ss.setStudent(student);
        ss.setStop(stop);
        ss.setAction(action);
        ss.setWeekdays(EnumSet.copyOf(List.of(days)));
        return ss;
    }

    private RouteStop routeStop(int order) {
        RouteStop stop = new RouteStop();
        stop.setId(UUID.randomUUID());
        stop.setOrderIndex(order);
        stop.setRoute(route);
        Address address = new Address();
        address.setLatitude(-23.5);
        address.setLongitude(-46.6);
        stop.setAddress(address);
        return stop;
    }

    private TripStartRequestDTO startRequest() {
        return new TripStartRequestDTO(-23.55, -46.63, vehicle.getId());
    }

    @Test
    void startCreatesBoardingsForTodaysStudentsAndMarksAnnouncedAbsences() {
        Student ana = student("Ana");
        Student bia = student("Bia");
        Student caio = student("Caio");
        RouteStop stop = routeStop(1);
        when(studentStopRepository.findByRouteWithStudent(route.getId())).thenReturn(List.of(
                studentStop(ana, stop, StudentStopAction.BOARDING, todayWeekDay),
                studentStop(bia, stop, StudentStopAction.BOARDING, todayWeekDay),
                // caio nao vai hoje
                studentStop(caio, stop, StudentStopAction.BOARDING, otherWeekDay)));
        StudentAbsence absence = new StudentAbsence();
        absence.setStudent(bia);
        when(studentAbsenceRepository.findByRouteIdAndDate(route.getId(), today)).thenReturn(List.of(absence));

        TripResponseDTO trip = tripService.start(route.getId(), startRequest(), false, driver.getUserId());

        assertEquals(TripStatus.STARTED, trip.status());
        ArgumentCaptor<Boarding> boardings = ArgumentCaptor.forClass(Boarding.class);
        verify(boardingRepository, times(2)).save(boardings.capture());
        Map<String, BoardingStatus> byName = new HashMap<>();
        boardings.getAllValues().forEach(b -> byName.put(b.getStudent().getName(), b.getStatus()));
        assertEquals(Map.of("Ana", BoardingStatus.PENDING, "Bia", BoardingStatus.ABSENT), byName);
        // "a van saiu" pros pais
        verify(tripNotificationService).tripStarted(any(Trip.class), eq(false));
        // estado ao vivo: posicao do motorista, trajeto pela API e aviso pro WebSocket
        verify(tripLiveService).setPosition(any(Trip.class), eq(-23.55), eq(-46.63));
        verify(tripLiveService).refresh(any(Trip.class), eq(true));
        verify(tripLiveService).publish(any());
    }

    @Test
    void startIsRejectedWhenTodaysTripAlreadyExists() {
        Trip existing = new Trip();
        existing.setStatus(TripStatus.IN_PROGRESS);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), today, TripStatus.CANCELED)).thenReturn(Optional.of(existing));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> tripService.start(route.getId(), startRequest(), false, driver.getUserId()));
        assertEquals(409, e.getStatusCode().value());
    }

    @Test
    void restartCancelsTodaysTripAndStartsAgain() {
        Trip existing = new Trip();
        existing.setId(UUID.randomUUID());
        existing.setStatus(TripStatus.IN_PROGRESS);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), today, TripStatus.CANCELED)).thenReturn(Optional.of(existing));
        when(studentStopRepository.findByRouteWithStudent(route.getId()))
                .thenReturn(List.of(studentStop(student("Ana"), routeStop(1), StudentStopAction.BOARDING, todayWeekDay)));

        tripService.start(route.getId(), startRequest(), true, driver.getUserId());

        assertEquals(TripStatus.CANCELED, existing.getStatus());
        verify(tripRepository).saveAndFlush(existing);
    }

    @Test
    void startIsRejectedWithIncompleteConfiguration() {
        when(routeService.pendingOf(route.getId())).thenReturn(List.of(
                new RoutePendingResponse(UUID.randomUUID(), "Ana", EnumSet.noneOf(WeekDay.class), EnumSet.of(WeekDay.MONDAY))));

        assertThrows(BadRequestException.class, () -> tripService.start(route.getId(), startRequest(), false, driver.getUserId()));
        verify(tripRepository, never()).save(any());
    }

    @Test
    void startNeedsAVanOfTheDriver() {
        TripStartRequestDTO withoutVan = new TripStartRequestDTO(-23.55, -46.63, null);

        assertThrows(BadRequestException.class, () -> tripService.start(route.getId(), withoutVan, false, driver.getUserId()));
    }

    private Trip activeTrip() {
        Trip trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setRoute(route);
        trip.setDriver(driver);
        trip.setDate(today);
        trip.setStatus(TripStatus.STARTED);
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));
        return trip;
    }

    private Boarding boarding(Trip trip, Student student, BoardingStatus status) {
        Boarding b = new Boarding();
        b.setTrip(trip);
        b.setStudent(student);
        b.setStatus(status);
        when(boardingRepository.findByTripIdAndStudentId(trip.getId(), student.getId())).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void boardingMovesPendingToBoardedAndStartsTheTrip() {
        Trip trip = activeTrip();
        Student ana = student("Ana");
        Boarding b = boarding(trip, ana, BoardingStatus.PENDING);

        tripService.board(trip.getId(), ana.getId(), driver.getUserId());

        assertEquals(BoardingStatus.BOARDED, b.getStatus());
        assertNotNull(b.getBoardingTime());
        verify(tripNotificationService).boarded(trip, ana);
        verify(tripNotificationService).nextStopChanged(eq(trip), any(), any());
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
        // embarque nao muda o caminho: atualiza sem chamar a API de rotas
        verify(tripLiveService).refresh(trip, false);
        assertThrows(ResponseStatusException.class, () -> tripService.board(trip.getId(), ana.getId(), driver.getUserId()));
    }

    @Test
    void driverCannotMarkAbsentAStudentAlreadyOnBoard() {
        Trip trip = activeTrip();
        Student ana = student("Ana");
        boarding(trip, ana, BoardingStatus.BOARDED);

        assertThrows(ResponseStatusException.class, () -> tripService.markAbsent(trip.getId(), ana.getId(), driver.getUserId()));
    }

    @Test
    void finishedTripCannotBeChanged() {
        Trip trip = activeTrip();
        trip.setStatus(TripStatus.FINISHED);
        Student ana = student("Ana");
        boarding(trip, ana, BoardingStatus.PENDING);

        assertThrows(ResponseStatusException.class, () -> tripService.board(trip.getId(), ana.getId(), driver.getUserId()));
    }

    @Test
    void announcedAbsenceDuringTheTripRemovesThePendingStudent() {
        Trip trip = activeTrip();
        Student ana = student("Ana");
        Boarding b = boarding(trip, ana, BoardingStatus.PENDING);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusIn(eq(route.getId()), eq(today), any())).thenReturn(Optional.of(trip));
        StudentAbsence absence = new StudentAbsence();
        absence.setStudent(ana);
        absence.setRoute(route);
        absence.setDate(today);

        tripService.applyAbsence(absence);
        assertEquals(BoardingStatus.ABSENT, b.getStatus());
        verify(tripLiveService).refresh(trip, true);
    }

    // van parada em (lat, lng); a Ana embarca no ponto em (-23.5000, -46.6000)
    private void vanAt(Trip trip, Student ana, double lat, double lng) {
        TripStudentDTO st = new TripStudentDTO(ana.getId(), "Ana", StudentStopAction.BOARDING, null, BoardingStatus.PENDING, null, null);
        TripLiveStopDTO stop = new TripLiveStopDTO(UUID.randomUUID(), 1, -23.5000, -46.6000, List.of(st), List.of(), false, null, null);
        when(tripLiveService.state(trip.getId())).thenReturn(Optional.of(new TripLiveStateDTO(trip.getId(), "Ida", TripStatus.STARTED,
                lat, lng, null, null, null, null, null, List.of(stop))));
    }

    @Test
    void boardingIsRefusedWhenTheVanIsFarFromTheStop() {
        ReflectionTestUtils.setField(tripService, "maxStopDistanceMeters", 1000.0);
        Trip trip = activeTrip();
        Student ana = student("Ana");
        Boarding b = boarding(trip, ana, BoardingStatus.PENDING);
        vanAt(trip, ana, -23.5200, -46.6000); // ~2,2 km

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> tripService.board(trip.getId(), ana.getId(), driver.getUserId()));
        assertEquals(409, e.getStatusCode().value());
        assertTrue(e.getReason().contains("2.2 km") || e.getReason().contains("2,2 km"), e.getReason());
        assertEquals(BoardingStatus.PENDING, b.getStatus());
    }

    @Test
    void boardingWorksNearTheStop() {
        ReflectionTestUtils.setField(tripService, "maxStopDistanceMeters", 1000.0);
        Trip trip = activeTrip();
        Student ana = student("Ana");
        Boarding b = boarding(trip, ana, BoardingStatus.PENDING);
        vanAt(trip, ana, -23.5030, -46.6000); // ~330 m

        tripService.board(trip.getId(), ana.getId(), driver.getUserId());

        assertEquals(BoardingStatus.BOARDED, b.getStatus());
    }

    @Test
    void boardingNeedsTheVanPosition() {
        ReflectionTestUtils.setField(tripService, "maxStopDistanceMeters", 1000.0);
        Trip trip = activeTrip();
        Student ana = student("Ana");
        boarding(trip, ana, BoardingStatus.PENDING);
        when(tripLiveService.state(trip.getId())).thenReturn(Optional.of(new TripLiveStateDTO(trip.getId(), "Ida", TripStatus.STARTED,
                null, null, null, null, null, null, null, List.of())));

        assertThrows(ResponseStatusException.class, () -> tripService.board(trip.getId(), ana.getId(), driver.getUserId()));
    }

    @Test
    void absenceIsRefusedOnceTheStudentBoarded() {
        Trip trip = activeTrip();
        Student ana = student("Ana");
        boarding(trip, ana, BoardingStatus.BOARDED);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), today, TripStatus.CANCELED)).thenReturn(Optional.of(trip));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> tripService.checkAbsenceAllowed(route.getId(), ana.getId(), today));
        assertEquals(409, e.getStatusCode().value());
    }

    @Test
    void absenceIsAllowedWhileStillWaitingOrBeforeTheTrip() {
        Trip trip = activeTrip();
        Student ana = student("Ana");
        boarding(trip, ana, BoardingStatus.PENDING);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), today, TripStatus.CANCELED)).thenReturn(Optional.of(trip));
        assertDoesNotThrow(() -> tripService.checkAbsenceAllowed(route.getId(), ana.getId(), today));

        // dia sem viagem ainda
        LocalDate tomorrow = today.plusDays(1);
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), tomorrow, TripStatus.CANCELED)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> tripService.checkAbsenceAllowed(route.getId(), ana.getId(), tomorrow));
        assertDoesNotThrow(() -> tripService.checkAbsenceUndoAllowed(route.getId(), tomorrow));
    }

    @Test
    void absenceCannotBeUndoneAfterTheTripStarted() {
        Trip trip = activeTrip();
        when(tripRepository.findFirstByRouteIdAndDateAndStatusNot(route.getId(), today, TripStatus.CANCELED)).thenReturn(Optional.of(trip));

        assertThrows(ResponseStatusException.class, () -> tripService.checkAbsenceUndoAllowed(route.getId(), today));
    }
}
