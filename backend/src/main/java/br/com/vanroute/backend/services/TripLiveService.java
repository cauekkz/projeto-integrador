package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.geocode.GeoRouteResponse;
import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.dtos.trip.*;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// estado ao vivo da trip (Redis) e o que cada um ve pelo WebSocket.
// a API de rotas e chamada no inicio, quando a rota muda (falta) e, com o GPS chegando:
//   - a cada trip.eta-refresh-seconds (5 min), ou
//   - na hora se a van saiu da linha (mais de trip.off-route-meters), com no minimo trip.off-route-min-seconds entre chamadas.
// entre uma chamada e outra os tempos so descontam. a cota da chave e pequena (200 rotas/dia): isso da ~12 chamadas por viagem de 1 h
@Service
public class TripLiveService {

    private static final Logger log = LoggerFactory.getLogger(TripLiveService.class);
    private static final Duration REDIS_TTL = Duration.ofHours(16);
    private static final Set<TripStatus> ACTIVE = EnumSet.of(TripStatus.STARTED, TripStatus.IN_PROGRESS, TripStatus.PAUSED);

    public record Access(boolean driver, Set<UUID> visibleStudents) {
    }

    private final TripRepository tripRepository;
    private final BoardingRepository boardingRepository;
    private final RouteStopRepository routeStopRepository;
    private final StudentStopRepository studentStopRepository;
    private final SchoolStopRepository schoolStopRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final MapService mapService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${trip.eta-refresh-seconds:300}")
    private long etaRefreshSeconds;

    @Value("${trip.off-route-meters:150}")
    private double offRouteMeters;

    @Value("${trip.off-route-min-seconds:30}")
    private long offRouteMinSeconds;

    public TripLiveService(TripRepository tripRepository, BoardingRepository boardingRepository,
                           RouteStopRepository routeStopRepository, StudentStopRepository studentStopRepository,
                           SchoolStopRepository schoolStopRepository, StudentResponsibleRepository studentResponsibleRepository,
                           MapService mapService, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.tripRepository = tripRepository;
        this.boardingRepository = boardingRepository;
        this.routeStopRepository = routeStopRepository;
        this.studentStopRepository = studentStopRepository;
        this.schoolStopRepository = schoolStopRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.mapService = mapService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // quem pode abrir o WebSocket da trip: o motorista dela, ou um responsavel de algum aluno dela (so ve os filhos dele)
    @Transactional(readOnly = true)
    public Access access(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        if (trip.getDriver() != null && trip.getDriver().getUserId().equals(userId)) {
            return new Access(true, Set.of());
        }
        Set<UUID> children = boardingRepository.findByTripWithStudent(tripId).stream()
                .map(boarding -> boarding.getStudent().getId())
                .filter(studentId -> studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(userId, studentId))
                .collect(Collectors.toSet());
        if (children.isEmpty()) {
            throw new AccessDeniedException("Você não acompanha essa trip");
        }
        return new Access(false, children);
    }

    // pontos de hoje, na ordem; ponto sem nenhum aluno hoje (ex.: escola sem aula) nao entra
    public List<TripStopDTO> buildStops(Trip trip) {
        WeekDay weekDay = WeekDay.valueOf(trip.getDate().getDayOfWeek().name());
        Map<UUID, Boarding> boardings = boardingRepository.findByTripWithStudent(trip.getId()).stream()
                .collect(Collectors.toMap(boarding -> boarding.getStudent().getId(), Function.identity()));
        List<TripStopDTO> stops = new ArrayList<>();
        for (RouteStop stop : routeStopRepository.findByRouteIdOrderByOrderIndexAsc(trip.getRoute().getId())) {
            List<TripStudentDTO> students = studentStopRepository.findByStopId(stop.getId()).stream()
                    .filter(studentStop -> studentStop.getWeekdays().contains(weekDay))
                    .filter(studentStop -> boardings.containsKey(studentStop.getStudent().getId()))
                    .map(studentStop -> new TripStudentDTO(
                            studentStop.getStudent().getId(),
                            studentStop.getStudent().getName(),
                            studentStop.getAction(),
                            studentStop.getLocationType(),
                            boardings.get(studentStop.getStudent().getId()).getStatus(),
                            boardings.get(studentStop.getStudent().getId()).getBoardingTime(),
                            boardings.get(studentStop.getStudent().getId()).getUnboardingTime()))
                    .toList();
            if (students.isEmpty()) {
                continue;
            }
            List<UUID> schoolIds = schoolStopRepository.findByStopId(stop.getId()).stream()
                    .map(schoolStop -> schoolStop.getSchool().getId())
                    .toList();
            boolean done = students.stream().noneMatch(TripLiveService::pendingAtStop);
            stops.add(new TripStopDTO(stop.getId(), stop.getOrderIndex(), toAddress(stop.getAddress()), students, schoolIds, done));
        }
        return stops;
    }

    // ainda precisa parar nesse ponto por causa desse aluno?
    public static boolean pendingAtStop(TripStudentDTO student) {
        if (student.status() == BoardingStatus.ABSENT || student.status() == BoardingStatus.DELIVERED) {
            return false;
        }
        return student.action() == StudentStopAction.ALIGHTING || student.status() == BoardingStatus.PENDING;
    }

    // GPS do motorista (chega pelo WebSocket)
    @Transactional(readOnly = true)
    public void updatePosition(UUID tripId, double latitude, double longitude) {
        TripLiveStateDTO current = state(tripId).orElse(null);
        if (current == null || !ACTIVE.contains(current.status())) {
            throw new IllegalStateException("A trip não está em andamento");
        }
        long now = System.currentTimeMillis();
        long sinceLast = current.computedAt() == null ? Long.MAX_VALUE : now - current.computedAt();
        boolean stale = sinceLast >= etaRefreshSeconds * 1000;
        // desviou (outro caminho, transito): a linha velha nao serve mais, recalcula antes do tempo
        boolean offRoute = !stale && sinceLast >= offRouteMinSeconds * 1000 && current.path() != null && current.path().size() > 1
                && distanceToPathMeters(latitude, longitude, current.path()) > offRouteMeters;
        TripLiveStateDTO moved = withPosition(current, latitude, longitude, now);
        save(moved);
        if (stale || offRoute) {
            Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
            refresh(trip, true);
        }
        publish(tripId);
    }

    public void setPosition(Trip trip, double latitude, double longitude) {
        TripLiveStateDTO current = state(trip.getId()).orElse(emptyState(trip));
        save(withPosition(current, latitude, longitude, System.currentTimeMillis()));
    }

    // reconstroi o estado (alunos e status por ponto). callApi: recalcula trajeto e tempos a partir da posicao da van;
    // sem callApi mantem a linha e os tempos do ultimo calculo (ex.: um aluno embarcou, o caminho nao mudou)
    public TripLiveStateDTO refresh(Trip trip, boolean callApi) {
        TripLiveStateDTO previous = state(trip.getId()).orElse(emptyState(trip));
        List<TripStopDTO> stops = buildStops(trip);

        List<List<Double>> path = previous.path();
        Double distance = previous.distance();
        Double duration = previous.duration();
        Long computedAt = previous.computedAt();
        Map<UUID, Integer> pathIndex = new HashMap<>();
        Map<UUID, Double> eta = new HashMap<>();
        if (previous.stops() != null) {
            previous.stops().forEach(stop -> {
                if (stop.pathIndex() != null) pathIndex.put(stop.routeStopId(), stop.pathIndex());
                if (stop.etaSeconds() != null) eta.put(stop.routeStopId(), stop.etaSeconds());
            });
        }

        if (callApi && previous.latitude() != null && ACTIVE.contains(trip.getStatus())) {
            List<TripStopDTO> remaining = stops.stream()
                    .filter(stop -> !stop.done())
                    .filter(stop -> stop.address().latitude() != null && stop.address().longitude() != null)
                    .toList();
            List<List<Double>> points = new ArrayList<>();
            points.add(List.of(previous.longitude(), previous.latitude()));
            remaining.forEach(stop -> points.add(List.of(stop.address().longitude(), stop.address().latitude())));
            if (remaining.isEmpty()) {
                path = points;
                distance = 0.0;
                duration = 0.0;
                computedAt = System.currentTimeMillis();
                pathIndex.clear();
                eta.clear();
            } else {
                try {
                    GeoRouteResponse.Feature feature = mapService.directions(points).features().get(0);
                    path = feature.geometry().coordinates();
                    distance = feature.properties().summary().distance();
                    duration = feature.properties().summary().duration();
                    computedAt = System.currentTimeMillis();
                    pathIndex.clear();
                    eta.clear();
                    // parada k (k = 1..n) fica em way_points[k]; tempo ate ela = soma dos trechos anteriores
                    List<Integer> wayPoints = feature.properties().wayPoints();
                    List<GeoRouteResponse.Segment> segments = feature.properties().segments();
                    double accumulated = 0;
                    for (int k = 0; k < remaining.size(); k++) {
                        if (segments != null && k < segments.size()) {
                            accumulated += segments.get(k).duration();
                            eta.put(remaining.get(k).routeStopId(), accumulated);
                        }
                        if (wayPoints != null && k + 1 < wayPoints.size()) {
                            pathIndex.put(remaining.get(k).routeStopId(), wayPoints.get(k + 1));
                        }
                    }
                } catch (Exception e) {
                    // sem a API a trip continua com o ultimo trajeto; tenta de novo no proximo calculo
                    String detail = e instanceof WebClientResponseException response ? response.getResponseBodyAsString() : "";
                    log.warn("Falha ao calcular o trajeto da trip {}: {} {}", trip.getId(), e.getMessage(), detail);
                }
            }
        }

        List<TripLiveStopDTO> liveStops = stops.stream()
                .map(stop -> new TripLiveStopDTO(
                        stop.routeStopId(),
                        stop.orderIndex(),
                        stop.address().latitude(),
                        stop.address().longitude(),
                        stop.students(),
                        stop.schoolIds(),
                        stop.done(),
                        stop.done() ? null : pathIndex.get(stop.routeStopId()),
                        stop.done() ? null : eta.get(stop.routeStopId())))
                .toList();
        TripLiveStateDTO state = new TripLiveStateDTO(trip.getId(), trip.getRoute().getName(), trip.getStatus(),
                previous.latitude(), previous.longitude(), previous.positionAt(), computedAt,
                distance, duration, path, liveStops);
        save(state);
        return state;
    }

    public Optional<TripLiveStateDTO> state(UUID tripId) {
        String json = redisTemplate.opsForValue().get(key(tripId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, TripLiveStateDTO.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // avisa todas as instancias do backend; cada uma manda a visao certa pros sockets conectados nela
    public void publish(UUID tripId) {
        redisTemplate.convertAndSend("trip-" + tripId, "update");
    }

    public TripLiveViewDTO view(TripLiveStateDTO state, Access access) {
        boolean active = ACTIVE.contains(state.status());
        long now = System.currentTimeMillis();
        List<TripLiveStopDTO> stops = state.stops() == null ? List.of() : state.stops().stream()
                .map(stop -> new TripLiveStopDTO(stop.routeStopId(), stop.orderIndex(), stop.latitude(), stop.longitude(),
                        stop.students(), stop.schoolIds(), stop.done(), stop.pathIndex(), remaining(stop.etaSeconds(), state.computedAt(), now)))
                .toList();
        TripLiveStopDTO next = stops.stream().filter(stop -> !stop.done()).findFirst().orElse(null);

        if (access.driver()) {
            List<TripStudentDTO> students = stops.stream().flatMap(stop -> stop.students().stream())
                    .collect(Collectors.toMap(TripStudentDTO::studentId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                    .values().stream().toList();
            return new TripLiveViewDTO("trip-update", state.tripId(), state.routeName(), state.status(), active, "DRIVER",
                    state.latitude(), state.longitude(), state.positionAt(),
                    next == null ? null : next.routeStopId(), next == null ? null : next.etaSeconds(),
                    active ? state.path() : null, students, stops);
        }

        List<TripStudentDTO> children = stops.stream().flatMap(stop -> stop.students().stream())
                .filter(student -> access.visibleStudents().contains(student.studentId()))
                .collect(Collectors.toMap(TripStudentDTO::studentId, Function.identity(),
                        // o status e o mesmo; pra tela do pai vale a acao que ainda falta
                        (a, b) -> pendingAtStop(a) ? a : b, LinkedHashMap::new))
                .values().stream().toList();
        boolean following = active && next != null && next.students().stream()
                .anyMatch(student -> access.visibleStudents().contains(student.studentId()) && pendingAtStop(student));
        if (!following) {
            // todos os filhos dele ja foram entregues (ou nao foram hoje), mas a rota continua pros outros:
            // acompanhamento encerrado pra esse pai (a hora da entrega vem em students[].deliveredAt)
            boolean done = active && !children.isEmpty() && children.stream()
                    .allMatch(student -> student.status() == BoardingStatus.DELIVERED || student.status() == BoardingStatus.ABSENT);
            if (done) {
                return new TripLiveViewDTO("trip-update", state.tripId(), state.routeName(), state.status(), false, "DONE",
                        null, null, null, null, null, null, children, null);
            }
            // filho dentro da van, mas ela ainda vai buscar outros: so a posicao da van (sem linha, pontos nem tempo)
            boolean onBoard = active && children.stream().anyMatch(student -> student.status() == BoardingStatus.BOARDED);
            if (onBoard) {
                return new TripLiveViewDTO("trip-update", state.tripId(), state.routeName(), state.status(), false, "ON_BOARD",
                        state.latitude(), state.longitude(), state.positionAt(), null, null, null, children, null);
            }
            return new TripLiveViewDTO("trip-update", state.tripId(), state.routeName(), state.status(), false, "STATUS",
                    null, null, null, null, null, null, children, null);
        }
        List<List<Double>> path = state.path();
        if (path != null && next.pathIndex() != null) {
            path = path.subList(0, Math.min(next.pathIndex() + 1, path.size()));
        }
        return new TripLiveViewDTO("trip-update", state.tripId(), state.routeName(), state.status(), true, "FOLLOWING",
                state.latitude(), state.longitude(), state.positionAt(), next.routeStopId(), next.etaSeconds(),
                path, children, null);
    }

    // menor distancia (m) do ponto ate a linha [lng, lat]; projecao plana local (erro desprezivel em poucos km)
    static double distanceToPathMeters(double lat, double lng, List<List<Double>> path) {
        double metersPerDegLat = 111_320;
        double metersPerDegLng = 111_320 * Math.cos(Math.toRadians(lat));
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < path.size(); i++) {
            double ax = (path.get(i).get(0) - lng) * metersPerDegLng, ay = (path.get(i).get(1) - lat) * metersPerDegLat;
            double bx = (path.get(i + 1).get(0) - lng) * metersPerDegLng, by = (path.get(i + 1).get(1) - lat) * metersPerDegLat;
            double dx = bx - ax, dy = by - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
        }
        return best;
    }

    private static Double remaining(Double eta, Long computedAt, long now) {
        if (eta == null || computedAt == null) {
            return null;
        }
        return Math.max(0, eta - (now - computedAt) / 1000.0);
    }

    private TripLiveStateDTO withPosition(TripLiveStateDTO s, double latitude, double longitude, long at) {
        return new TripLiveStateDTO(s.tripId(), s.routeName(), s.status(), latitude, longitude, at, s.computedAt(),
                s.distance(), s.duration(), s.path(), s.stops());
    }

    private TripLiveStateDTO emptyState(Trip trip) {
        return new TripLiveStateDTO(trip.getId(), trip.getRoute().getName(), trip.getStatus(),
                null, null, null, null, null, null, null, null);
    }

    private void save(TripLiveStateDTO state) {
        try {
            redisTemplate.opsForValue().set(key(state.tripId()), objectMapper.writeValueAsString(state), REDIS_TTL);
        } catch (Exception e) {
            log.warn("Falha ao guardar o estado da trip {}", state.tripId(), e);
        }
    }

    private static String key(UUID tripId) {
        return "trip:" + tripId + ":live";
    }

    private static AddressResponseDTO toAddress(Address address) {
        return new AddressResponseDTO(
                address.getId(),
                address.getStreet(),
                address.getZipCode(),
                address.getCity(),
                address.getNeighborhood(),
                address.getNumber(),
                address.getState(),
                address.getLatitude(),
                address.getLongitude());
    }
}
