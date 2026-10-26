package br.com.vanroute.backend.services;

import br.com.vanroute.backend.config.JacksonConfig;
import br.com.vanroute.backend.dtos.geocode.GeoRouteResponse;
import br.com.vanroute.backend.dtos.trip.*;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripLiveServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private BoardingRepository boardingRepository;
    @Mock private RouteStopRepository routeStopRepository;
    @Mock private StudentStopRepository studentStopRepository;
    @Mock private SchoolStopRepository schoolStopRepository;
    @Mock private StudentResponsibleRepository studentResponsibleRepository;
    @Mock private MapService mapService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private final Map<String, String> redis = new HashMap<>();
    private TripLiveService live;

    private final LocalDate today = LocalDate.now(TripService.ZONE);
    private final WeekDay todayWeekDay = WeekDay.valueOf(today.getDayOfWeek().name());
    private final WeekDay otherWeekDay = WeekDay.values()[(todayWeekDay.ordinal() + 1) % 7];
    private Trip trip;
    private Student ana;
    private Student bia;
    private RouteStop anaHome;
    private RouteStop biaHome;
    private RouteStop school;

    @BeforeEach
    void setUp() {
        live = new TripLiveService(tripRepository, boardingRepository, routeStopRepository, studentStopRepository,
                schoolStopRepository, studentResponsibleRepository, mapService, redisTemplate, objectMapper);
        ReflectionTestUtils.setField(live, "etaRefreshSeconds", 60L);
        ReflectionTestUtils.setField(live, "offRouteMeters", 150.0);
        ReflectionTestUtils.setField(live, "offRouteMinSeconds", 30L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1))).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(inv -> redis.get((String) inv.getArgument(0)));

        Route route = new Route();
        route.setId(UUID.randomUUID());
        route.setName("Ida");
        trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setRoute(route);
        trip.setDate(today);
        trip.setStatus(TripStatus.STARTED);
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        ana = student("Ana");
        bia = student("Bia");
        anaHome = stop(1);
        biaHome = stop(2);
        school = stop(3);
        when(routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId())).thenReturn(List.of(anaHome, biaHome, school));
        when(studentStopRepository.findByStopId(anaHome.getId())).thenReturn(List.of(studentStop(ana, anaHome, StudentStopAction.BOARDING, todayWeekDay)));
        when(studentStopRepository.findByStopId(biaHome.getId())).thenReturn(List.of(studentStop(bia, biaHome, StudentStopAction.BOARDING, todayWeekDay)));
        when(studentStopRepository.findByStopId(school.getId())).thenReturn(List.of(
                studentStop(ana, school, StudentStopAction.ALIGHTING, todayWeekDay),
                studentStop(bia, school, StudentStopAction.ALIGHTING, todayWeekDay)));
        boardings(BoardingStatus.PENDING, BoardingStatus.PENDING);

        // trajeto falso: 3 paradas, trechos de 60s, 120s e 300s; paradas nas posicoes 4, 7 e 10 da linha
        List<List<Double>> line = new ArrayList<>();
        for (int i = 0; i <= 10; i++) line.add(List.of(-46.6 + i * 0.001, -23.5));
        GeoRouteResponse response = new GeoRouteResponse(List.of(new GeoRouteResponse.Feature(
                new GeoRouteResponse.Properties(new GeoRouteResponse.Summary(5000, 480),
                        List.of(new GeoRouteResponse.Segment(1000, 60), new GeoRouteResponse.Segment(1500, 120), new GeoRouteResponse.Segment(2500, 300)),
                        List.of(0, 4, 7, 10)),
                new GeoRouteResponse.Geometry("LineString", line))));
        when(mapService.directions(anyList())).thenReturn(response);
    }

    private Student student(String name) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private RouteStop stop(int order) {
        RouteStop stop = new RouteStop();
        stop.setId(UUID.randomUUID());
        stop.setOrderIndex(order);
        Address address = new Address();
        address.setLatitude(-23.5);
        address.setLongitude(-46.6 + order * 0.01);
        stop.setAddress(address);
        return stop;
    }

    private StudentStop studentStop(Student student, RouteStop stop, StudentStopAction action, WeekDay... days) {
        StudentStop ss = new StudentStop();
        ss.setStudent(student);
        ss.setStop(stop);
        ss.setAction(action);
        ss.setWeekdays(EnumSet.copyOf(List.of(days)));
        return ss;
    }

    private void boardings(BoardingStatus anaStatus, BoardingStatus biaStatus) {
        Boarding a = new Boarding();
        a.setStudent(ana);
        a.setStatus(anaStatus);
        Boarding b = new Boarding();
        b.setStudent(bia);
        b.setStatus(biaStatus);
        when(boardingRepository.findByTripWithStudent(trip.getId())).thenReturn(List.of(a, b));
    }

    private TripLiveService.Access parentOf(Student student) {
        return new TripLiveService.Access(false, Set.of(student.getId()));
    }

    @Test
    void stopsOfTheDaySkipStopsWithNobodyToday() {
        when(studentStopRepository.findByStopId(biaHome.getId()))
                .thenReturn(List.of(studentStop(bia, biaHome, StudentStopAction.BOARDING, otherWeekDay)));

        List<TripStopDTO> stops = live.buildStops(trip);

        assertEquals(List.of(anaHome.getId(), school.getId()), stops.stream().map(TripStopDTO::routeStopId).toList());
    }

    @Test
    void refreshComputesTimeUntilEachStopFromTheApiSegments() {
        live.setPosition(trip, -23.55, -46.63);

        TripLiveStateDTO state = live.refresh(trip, true);

        assertEquals(List.of(60.0, 180.0, 480.0), state.stops().stream().map(TripLiveStopDTO::etaSeconds).toList());
        assertEquals(List.of(4, 7, 10), state.stops().stream().map(TripLiveStopDTO::pathIndex).toList());
        verify(mapService, times(1)).directions(anyList());
    }

    @Test
    void parentFollowsOnlyWhileTheNextStopIsTheirChilds() {
        live.setPosition(trip, -23.55, -46.63);
        TripLiveStateDTO state = live.refresh(trip, true);

        // proximo ponto: casa da Ana
        TripLiveViewDTO anaParent = live.view(state, parentOf(ana));
        assertTrue(anaParent.following());
        assertEquals(anaHome.getId(), anaParent.targetStopId());
        assertEquals(5, anaParent.path().size(), "linha so ate a casa da Ana (posicoes 0..4)");
        assertNotNull(anaParent.latitude());
        assertEquals(List.of(ana.getId()), anaParent.students().stream().map(TripStudentDTO::studentId).toList());

        TripLiveViewDTO biaParent = live.view(state, parentOf(bia));
        assertFalse(biaParent.following(), "a van ainda nao esta indo pra casa da Bia");
        assertEquals("STATUS", biaParent.mode(), "Bia ainda nao embarcou: so o status");
        assertNull(biaParent.latitude());
        assertNull(biaParent.path());

        // Ana embarcou: agora a van vai pra casa da Bia
        boardings(BoardingStatus.BOARDED, BoardingStatus.PENDING);
        state = live.refresh(trip, false);
        TripLiveViewDTO anaOnBoard = live.view(state, parentOf(ana));
        assertFalse(anaOnBoard.following(), "Ana ja embarcou; a rota so volta a aparecer quando for pra escola");
        // mas a mae ve onde a van esta (so a van: sem linha, sem ponto e sem tempo)
        assertEquals("ON_BOARD", anaOnBoard.mode());
        assertNotNull(anaOnBoard.latitude());
        assertNull(anaOnBoard.path());
        assertNull(anaOnBoard.targetStopId());
        assertNull(anaOnBoard.etaSeconds());
        TripLiveViewDTO biaNow = live.view(state, parentOf(bia));
        assertTrue(biaNow.following());
        assertEquals(8, biaNow.path().size(), "linha ate a casa da Bia (posicoes 0..7)");

        // Bia embarcou: os dois pais acompanham a ida pra escola
        boardings(BoardingStatus.BOARDED, BoardingStatus.BOARDED);
        state = live.refresh(trip, false);
        assertTrue(live.view(state, parentOf(ana)).following());
        assertEquals(school.getId(), live.view(state, parentOf(ana)).targetStopId());

        // Ana entregue, Bia ainda nao: a rota segue, a mae da Ana ve "acompanhamento encerrado"
        boardings(BoardingStatus.DELIVERED, BoardingStatus.BOARDED);
        state = live.refresh(trip, false);
        assertEquals("DONE", live.view(state, parentOf(ana)).mode());
        assertNull(live.view(state, parentOf(ana)).latitude());
        assertEquals("FOLLOWING", live.view(state, parentOf(bia)).mode());

        // entregues: ninguem acompanha mais
        boardings(BoardingStatus.DELIVERED, BoardingStatus.DELIVERED);
        state = live.refresh(trip, false);
        assertFalse(live.view(state, parentOf(ana)).following());
        assertFalse(live.view(state, parentOf(bia)).following());
    }

    @Test
    void driverSeesEverything() {
        live.setPosition(trip, -23.55, -46.63);
        TripLiveStateDTO state = live.refresh(trip, true);

        TripLiveViewDTO driver = live.view(state, new TripLiveService.Access(true, Set.of()));

        assertTrue(driver.following());
        assertEquals(3, driver.stops().size());
        assertEquals(2, driver.students().size());
        assertEquals(11, driver.path().size());
    }

    @Test
    void gpsOnlyCallsTheApiWhenTheLastCalculationIsOld() {
        live.setPosition(trip, -23.55, -46.63);
        live.refresh(trip, true);
        clearInvocations(mapService);

        live.updatePosition(trip.getId(), -23.551, -46.631);
        verify(mapService, never()).directions(anyList());
        assertEquals(-23.551, live.state(trip.getId()).orElseThrow().latitude());

        ReflectionTestUtils.setField(live, "etaRefreshSeconds", 0L);
        live.updatePosition(trip.getId(), -23.552, -46.632);
        verify(mapService, times(1)).directions(anyList());
        verify(redisTemplate, times(2)).convertAndSend("trip-" + trip.getId(), "update");
    }

    @Test
    void leavingTheLineRecalculatesBeforeTheInterval() {
        ReflectionTestUtils.setField(live, "offRouteMinSeconds", 0L);
        live.setPosition(trip, -23.55, -46.63);
        live.refresh(trip, true);
        clearInvocations(mapService);
        // a linha falsa vai de (-23.5, -46.6) a (-23.5, -46.59)

        // em cima da linha: nao chama a API
        live.updatePosition(trip.getId(), -23.5, -46.595);
        verify(mapService, never()).directions(anyList());

        // 1 km pro lado (desviou): recalcula na hora
        live.updatePosition(trip.getId(), -23.509, -46.595);
        verify(mapService, times(1)).directions(anyList());
    }

    @Test
    void distanceToTheLine() {
        List<List<Double>> line = List.of(List.of(-46.6, -23.5), List.of(-46.59, -23.5));
        assertEquals(0, TripLiveService.distanceToPathMeters(-23.5, -46.595, line), 1);
        // 0,001 grau de latitude ~ 111 m
        assertEquals(111, TripLiveService.distanceToPathMeters(-23.501, -46.595, line), 2);
    }

    @Test
    void timeLeftKeepsGoingDownBetweenCalculations() {
        live.setPosition(trip, -23.55, -46.63);
        TripLiveStateDTO state = live.refresh(trip, true);
        TripLiveStateDTO tenSecondsLater = new TripLiveStateDTO(state.tripId(), state.routeName(), state.status(),
                state.latitude(), state.longitude(), state.positionAt(), state.computedAt() - 10_000,
                state.distance(), state.duration(), state.path(), state.stops());

        Double eta = live.view(tenSecondsLater, parentOf(ana)).etaSeconds();

        assertEquals(50.0, eta, 0.5);
    }

    @Test
    void outsiderCannotFollowTheTrip() {
        Boarding a = new Boarding();
        a.setStudent(ana);
        when(boardingRepository.findByTripWithStudent(trip.getId())).thenReturn(List.of(a));
        UUID stranger = UUID.randomUUID();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> live.access(trip.getId(), stranger));
    }
}
