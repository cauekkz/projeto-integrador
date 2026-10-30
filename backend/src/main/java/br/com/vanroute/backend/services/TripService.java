package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.RoutePendingResponse;
import br.com.vanroute.backend.dtos.trip.*;
import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.trip.Boarding;
import br.com.vanroute.backend.models.trip.StudentAbsence;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.vehicle.Vehicle;
import br.com.vanroute.backend.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

// execucao da rota: a trip e a viagem de um dia; cada aluno do dia vira um boarding.
// o tempo real (GPS, mapa, tempo ate o ponto) fica no TripLiveService + WebSocket /trip/{tripId}
@Service
public class TripService {

    // "hoje" e sempre no horario de Sao Paulo, independente do fuso do servidor
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<TripStatus> ACTIVE = EnumSet.of(TripStatus.STARTED, TripStatus.IN_PROGRESS, TripStatus.PAUSED);

    private final TripRepository tripRepository;
    private final BoardingRepository boardingRepository;
    private final StudentAbsenceRepository studentAbsenceRepository;
    private final RouteRepository routeRepository;
    private final RouteDriverRepository routeDriverRepository;
    private final StudentStopRepository studentStopRepository;
    private final DriverRepository driverRepository;
    private final DriverVehicleRepository driverVehicleRepository;
    private final VehicleRepository vehicleRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final RouteService routeService;
    private final TripLiveService tripLiveService;
    private final TripNotificationService tripNotificationService;

    // embarque/desembarque so com a van perto do ponto do aluno (0 desliga)
    @Value("${trip.max-stop-distance-meters:1000}")
    private double maxStopDistanceMeters;

    public TripService(TripRepository tripRepository, BoardingRepository boardingRepository,
                       StudentAbsenceRepository studentAbsenceRepository, RouteRepository routeRepository,
                       RouteDriverRepository routeDriverRepository, StudentStopRepository studentStopRepository,
                       DriverRepository driverRepository, DriverVehicleRepository driverVehicleRepository,
                       VehicleRepository vehicleRepository, StudentResponsibleRepository studentResponsibleRepository,
                       RouteService routeService, TripLiveService tripLiveService,
                       TripNotificationService tripNotificationService) {
        this.tripRepository = tripRepository;
        this.boardingRepository = boardingRepository;
        this.studentAbsenceRepository = studentAbsenceRepository;
        this.routeRepository = routeRepository;
        this.routeDriverRepository = routeDriverRepository;
        this.studentStopRepository = studentStopRepository;
        this.driverRepository = driverRepository;
        this.driverVehicleRepository = driverVehicleRepository;
        this.vehicleRepository = vehicleRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.routeService = routeService;
        this.tripLiveService = tripLiveService;
        this.tripNotificationService = tripNotificationService;
    }

    // botao "iniciar rota": cria a trip de hoje a partir da posicao atual do motorista.
    // ja existe trip hoje: 409, a nao ser que restart=true (cancela a antiga e comeca do zero)
    @Transactional
    public TripResponseDTO start(UUID routeId, TripStartRequestDTO request, boolean restart, UUID driverId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));
        Driver driver = routeOwner(routeId, driverId);
        // configuracao incompleta (aluno com embarque e sem desembarque num dia, ou o contrario): nao inicia
        List<RoutePendingResponse> pending = routeService.pendingOf(routeId);
        if (!pending.isEmpty()) {
            throw new BadRequestException("A rota tem alunos sem embarque ou desembarque em algum dia: " + pending.stream()
                    .map(p -> p.studentName()
                            + (p.missingBoarding().isEmpty() ? "" : " sem embarque " + p.missingBoarding())
                            + (p.missingAlighting().isEmpty() ? "" : " sem desembarque " + p.missingAlighting()))
                    .collect(Collectors.joining("; ")));
        }

        LocalDate today = LocalDate.now(ZONE);
        Optional<Trip> existing = tripRepository.findFirstByRouteIdAndDateAndStatusNot(routeId, today, TripStatus.CANCELED);
        if (existing.isPresent()) {
            Trip old = existing.get();
            //hemoglobina
            if (!restart) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma trip de hoje nessa rota: continue ou recomece");
            }
            if (old.getStatus() == TripStatus.FINISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa rota já foi finalizada hoje");
            }
            old.setStatus(TripStatus.CANCELED);
            old.setEndTime(LocalTime.now(ZONE));
            tripRepository.saveAndFlush(old);
            // quem estava acompanhando a trip antiga ve que ela foi cancelada
            tripLiveService.refresh(old, false);
            tripLiveService.publish(old.getId());
        }

        Vehicle vehicle = resolveVehicle(route, request.vehicleId(), driverId);

        // alunos de hoje: quem tem student stop nessa rota com o dia da semana de hoje (a ordem dos pontos vem depois)
        WeekDay weekDay = WeekDay.valueOf(today.getDayOfWeek().name());
        Map<UUID, Student> students = new LinkedHashMap<>();
        studentStopRepository.findByRouteWithStudent(routeId).stream()
                .filter(stop -> stop.getWeekdays().contains(weekDay))
                .forEach(stop -> students.putIfAbsent(stop.getStudent().getId(), stop.getStudent()));
        if (students.isEmpty()) {
            throw new BadRequestException("Nenhum aluno nessa rota hoje");
        }
        // falta avisada pelo responsavel pra essa rota hoje: ja entra ABSENT
        Set<UUID> absent = studentAbsenceRepository.findByRouteIdAndDate(routeId, today).stream()
                .map(absence -> absence.getStudent().getId())
                .collect(Collectors.toSet());

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        trip.setDate(today);
        trip.setStartTime(LocalTime.now(ZONE));
        trip.setStatus(TripStatus.STARTED);
        trip = tripRepository.save(trip);

        for (Student student : students.values()) {
            Boarding boarding = new Boarding();
            boarding.setTrip(trip);
            boarding.setStudent(student);
            boarding.setStatus(absent.contains(student.getId()) ? BoardingStatus.ABSENT : BoardingStatus.PENDING);
            boardingRepository.save(boarding);
        }

        tripLiveService.setPosition(trip, request.latitude(), request.longitude());
        TripLiveStateDTO state = tripLiveService.refresh(trip, true);
        tripLiveService.publish(trip.getId());
        // "a van saiu" pros pais e "a van esta indo buscar" pros do primeiro ponto
        tripNotificationService.tripStarted(trip, existing.isPresent());
        tripNotificationService.nextStopChanged(trip, null, state);
        return toResponse(trip);
    }

    // trip de hoje da rota (o app reabriu: o motorista escolhe continuar ou recomecar)
    public TripResponseDTO todayTrip(UUID routeId, UUID driverId) {
        routeOwner(routeId, driverId);
        Trip trip = tripRepository.findFirstByRouteIdAndDateAndStatusNot(routeId, LocalDate.now(ZONE), TripStatus.CANCELED)
                .orElseThrow(() -> new EntityNotFoundException("Nenhuma trip hoje nessa rota"));
        return toResponse(trip);
    }

    public TripResponseDTO getTrip(UUID tripId, UUID driverId) {
        return toResponse(ownTrip(tripId, driverId));
    }

    @Transactional
    public TripStudentDTO board(UUID tripId, UUID studentId, UUID driverId) {
        Trip trip = activeOwnTrip(tripId, driverId);
        Boarding boarding = boardingOf(tripId, studentId);
        if (boarding.getStatus() != BoardingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno não está aguardando embarque");
        }
        checkNearStop(tripId, studentId, StudentStopAction.BOARDING);
        boarding.setStatus(BoardingStatus.BOARDED);
        boarding.setBoardingTime(LocalDateTime.now(ZONE));
        if (trip.getStatus() == TripStatus.STARTED) {
            trip.setStatus(TripStatus.IN_PROGRESS);
        }
        // o caminho nao muda (a van segue), so o proximo ponto: sem chamar a API
        TripLiveStateDTO before = tripLiveService.state(tripId).orElse(null);
        TripLiveStateDTO after = tripLiveService.refresh(trip, false);
        tripLiveService.publish(tripId);
        tripNotificationService.boarded(trip, boarding.getStudent());
        tripNotificationService.nextStopChanged(trip, before, after);
        return toStudent(boarding);
    }

    @Transactional
    public TripStudentDTO alight(UUID tripId, UUID studentId, UUID driverId) {
        Trip trip = activeOwnTrip(tripId, driverId);
        Boarding boarding = boardingOf(tripId, studentId);
        if (boarding.getStatus() != BoardingStatus.BOARDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno não está embarcado");
        }
        checkNearStop(tripId, studentId, StudentStopAction.ALIGHTING);
        boarding.setStatus(BoardingStatus.DELIVERED);
        boarding.setUnboardingTime(LocalDateTime.now(ZONE));
        TripLiveStateDTO before = tripLiveService.state(tripId).orElse(null);
        TripLiveStateDTO after = tripLiveService.refresh(trip, false);
        tripLiveService.publish(tripId);
        tripNotificationService.delivered(trip, boarding.getStudent(), after);
        tripNotificationService.nextStopChanged(trip, before, after);
        return toStudent(boarding);
    }

    // motorista pula o aluno no meio da trip (nao apareceu, ninguem respondeu, o pai mandou mensagem);
    // so essa trip, a configuracao nao muda; o ponto pode sair do caminho, entao recalcula
    @Transactional
    public TripResponseDTO markAbsent(UUID tripId, UUID studentId, UUID driverId) {
        Trip trip = activeOwnTrip(tripId, driverId);
        Boarding boarding = boardingOf(tripId, studentId);
        if (boarding.getStatus() != BoardingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Só aluno que ainda não embarcou pode ficar ausente");
        }
        boarding.setStatus(BoardingStatus.ABSENT);
        TripLiveStateDTO before = tripLiveService.state(tripId).orElse(null);
        TripLiveStateDTO after = tripLiveService.refresh(trip, true);
        tripLiveService.publish(tripId);
        tripNotificationService.absentByDriver(trip, boarding.getStudent());
        tripNotificationService.nextStopChanged(trip, before, after);
        return toResponse(trip);
    }

    @Transactional
    public TripResponseDTO finish(UUID tripId, UUID driverId) {
        Trip trip = activeOwnTrip(tripId, driverId);
        trip.setStatus(TripStatus.FINISHED);
        trip.setEndTime(LocalTime.now(ZONE));
        tripLiveService.refresh(trip, false);
        tripLiveService.publish(tripId);
        return toResponse(trip);
    }

    // trips de hoje em que o filho esta, pro responsavel achar o tripId e abrir o WebSocket
    public List<StudentTripTodayDTO> studentTripsToday(UUID studentId, UUID userId) {
        if (!studentResponsibleRepository.existsByResponsible_UserIdAndStudent_Id(userId, studentId)) {
            throw new AccessDeniedException("Responsável não vinculado a esse aluno");
        }
        return boardingRepository.findTodayOfStudent(studentId, LocalDate.now(ZONE)).stream()
                .map(boarding -> new StudentTripTodayDTO(
                        boarding.getTrip().getId(),
                        boarding.getTrip().getRoute().getId(),
                        boarding.getTrip().getRoute().getName(),
                        boarding.getTrip().getStatus(),
                        boarding.getStatus()))
                .toList();
    }

    // o responsavel pode avisar falta enquanto o filho ainda nao embarcou; com a rota do dia finalizada, nao
    public void checkAbsenceAllowed(UUID routeId, UUID studentId, LocalDate date) {
        tripRepository.findFirstByRouteIdAndDateAndStatusNot(routeId, date, TripStatus.CANCELED).ifPresent(trip -> {
            if (trip.getStatus() == TripStatus.FINISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A rota desse dia já terminou");
            }
            boardingRepository.findByTripIdAndStudentId(trip.getId(), studentId).ifPresent(boarding -> {
                switch (boarding.getStatus()) {
                    case BOARDED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno já embarcou");
                    case DELIVERED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno já foi entregue");
                    case ABSENT -> throw new ResponseStatusException(HttpStatus.CONFLICT, "O aluno já está ausente nessa viagem");
                    default -> { }
                }
            });
        });
    }

    // desfazer a falta so antes da viagem do dia comecar (depois disso o aluno ja saiu da rota)
    public void checkAbsenceUndoAllowed(UUID routeId, LocalDate date) {
        if (tripRepository.findFirstByRouteIdAndDateAndStatusNot(routeId, date, TripStatus.CANCELED).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A viagem já começou: a falta não pode mais ser desfeita");
        }
    }

    // falta avisada pelo responsavel durante a trip: o aluno sai na hora e o trajeto e recalculado
    @Transactional
    public void applyAbsence(StudentAbsence absence) {
        tripRepository.findFirstByRouteIdAndDateAndStatusIn(absence.getRoute().getId(), absence.getDate(), ACTIVE)
                .ifPresent(trip -> boardingRepository.findByTripIdAndStudentId(trip.getId(), absence.getStudent().getId())
                        .filter(boarding -> boarding.getStatus() == BoardingStatus.PENDING)
                        .ifPresent(boarding -> {
                            boarding.setStatus(BoardingStatus.ABSENT);
                            TripLiveStateDTO before = tripLiveService.state(trip.getId()).orElse(null);
                            TripLiveStateDTO after = tripLiveService.refresh(trip, true);
                            tripLiveService.publish(trip.getId());
                            tripNotificationService.nextStopChanged(trip, before, after);
                        }));
    }

    // a van (ultima posicao do GPS) tem que estar perto do ponto onde o aluno embarca/desce hoje
    private void checkNearStop(UUID tripId, UUID studentId, StudentStopAction action) {
        if (maxStopDistanceMeters <= 0) {
            return;
        }
        TripLiveStateDTO state = tripLiveService.state(tripId).orElse(null);
        if (state == null || state.stops() == null) {
            return;
        }
        if (state.latitude() == null || state.longitude() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sem a posição da van: ligue o GPS do celular");
        }
        state.stops().stream()
                .filter(stop -> stop.latitude() != null && stop.longitude() != null)
                .filter(stop -> stop.students().stream().anyMatch(s -> s.studentId().equals(studentId) && s.action() == action))
                .findFirst()
                .ifPresent(stop -> {
                    double distance = distanceMeters(state.latitude(), state.longitude(), stop.latitude(), stop.longitude());
                    if (distance > maxStopDistanceMeters) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                                "A van está a %.1f km do ponto do aluno: chegue a menos de %.1f km",
                                distance / 1000, maxStopDistanceMeters / 1000));
                    }
                });
    }

    // distancia em linha reta entre dois pontos (haversine)
    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Driver routeOwner(UUID routeId, UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));
        if (!routeDriverRepository.existsByRouteIdAndDriverUserId(routeId, driverId)) {
            throw new AccessDeniedException("Route does not belong to this driver");
        }
        return driver;
    }

    private Trip ownTrip(UUID tripId, UUID driverId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found"));
        if (trip.getDriver() == null || !trip.getDriver().getUserId().equals(driverId)) {
            throw new AccessDeniedException("Trip de outro motorista");
        }
        return trip;
    }

    private Trip activeOwnTrip(UUID tripId, UUID driverId) {
        Trip trip = ownTrip(tripId, driverId);
        if (!ACTIVE.contains(trip.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A trip não está em andamento");
        }
        return trip;
    }

    private Boarding boardingOf(UUID tripId, UUID studentId) {
        return boardingRepository.findByTripIdAndStudentId(tripId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Aluno não está nessa trip"));
    }

    // van escolhida ao iniciar ou a van padrao da rota; tem que ser uma van ativa do motorista
    private Vehicle resolveVehicle(Route route, UUID vehicleId, UUID driverId) {
        UUID chosen = vehicleId != null ? vehicleId : (route.getVehicle() != null ? route.getVehicle().getId() : null);
        if (chosen == null) {
            throw new BadRequestException("Escolha a van da trip (ou defina a van padrão da rota)");
        }
        if (driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driverId, chosen).isEmpty()) {
            throw new BadRequestException("Essa van não está vinculada a você");
        }
        return vehicleRepository.getReferenceById(chosen);
    }

    private TripResponseDTO toResponse(Trip trip) {
        Vehicle vehicle = trip.getVehicle();
        TripPathDTO path = tripLiveService.state(trip.getId())
                .filter(state -> state.path() != null)
                .map(state -> new TripPathDTO(state.distance(), state.duration(), state.path()))
                .orElse(null);
        return new TripResponseDTO(
                trip.getId(),
                trip.getRoute().getId(),
                trip.getRoute().getName(),
                trip.getDate(),
                trip.getStatus(),
                trip.getStartTime(),
                trip.getEndTime(),
                vehicle == null ? null : vehicle.getId(),
                vehicle == null ? null : vehicle.getPlate(),
                tripLiveService.buildStops(trip),
                path);
    }

    private TripStudentDTO toStudent(Boarding boarding) {
        return new TripStudentDTO(boarding.getStudent().getId(), boarding.getStudent().getName(), null, null, boarding.getStatus(),
                boarding.getBoardingTime(), boarding.getUnboardingTime());
    }
}
