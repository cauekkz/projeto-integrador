package br.com.vanroute.backend.services;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.geocode.Coordenadas;
import br.com.vanroute.backend.exceptions.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import br.com.vanroute.backend.dtos.route.*;
import br.com.vanroute.backend.dtos.route.stops.SchoolStopResponseDTO;
import br.com.vanroute.backend.models.address.Address;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.models.route.RouteDriver;
import br.com.vanroute.backend.models.route.RouteStop;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.repositories.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import br.com.vanroute.backend.models.shared.enums.WeekDay;
import br.com.vanroute.backend.models.student.Student;
import br.com.vanroute.backend.models.student.StudentStop;
import br.com.vanroute.backend.models.student.enums.StudentStopAction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final AddressRepository addressRepository;
    private final RouteStopRepository routeStopRepository;
    private final DriverRepository driverRepository;
    private final RouteDriverRepository routeDriverRepository;
    private final AddressService addressService;
    private final StudentStopRepository studentStopRepository;
    private final SchoolStopRepository schoolStopRepository;
    private final DriverVehicleRepository driverVehicleRepository;
    private final VehicleRepository vehicleRepository;
    private final MapService mapService;


    public RouteService(RouteRepository routeRepository, AddressRepository addressRepository, RouteStopRepository routeStopRepository,
    DriverRepository driverRepository, RouteDriverRepository routeDriverRepository, AddressService addressService, MapService mapService,
    StudentStopRepository studentStopRepository, SchoolStopRepository schoolStopRepository,
    DriverVehicleRepository driverVehicleRepository, VehicleRepository vehicleRepository) {
        this.routeRepository = routeRepository;
        this.addressRepository = addressRepository;
        this.routeStopRepository = routeStopRepository;
        this.driverRepository = driverRepository;
        this.routeDriverRepository = routeDriverRepository;
        this.addressService = addressService;
        this.studentStopRepository = studentStopRepository;
        this.schoolStopRepository = schoolStopRepository;
        this.driverVehicleRepository = driverVehicleRepository;
        this.vehicleRepository = vehicleRepository;
        this.mapService = mapService;
    }

    @Transactional
    public RouteResponse createRoute(CreateRouteRequestDTO createRouteRequestDTO, Authentication authentication) {

        Route route = new Route();
        route.setName(createRouteRequestDTO.name());
        Route routeSaved = routeRepository.save(route);
        Driver driver = driverRepository.findById(AuthenticatedUser.id(authentication))
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));
        RouteDriver routeDriver = new RouteDriver();
        routeDriver.setRoute(route);
        routeDriver.setDriver(driver);
        routeDriver.setStartDate(LocalDate.now());
        routeDriver.setEndDate(null);
        routeDriverRepository.save(routeDriver);

        return new RouteResponse(
                routeSaved.getId(),
                routeSaved.getName(),
                driver.getUser().getName()
        );
    }

    @Transactional
    public List<RouteStopResponse> addRouteStop(UUID routeId, List<AddRouteStopRequest> addRouteStopRequest, Authentication authentication) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Route doesn't exists"));
        checkOwnership(route, authentication);
        List<RouteStopResponse> response = new ArrayList<>();
        for (AddRouteStopRequest request : addRouteStopRequest) {
            Address address = addressService.addAddress(request.address());
            Coordenadas cords = mapService.searchCoord(address);
            address.setLatitude(cords.latitude());
            address.setLongitude(cords.longitude());

            RouteStop routeStop = new RouteStop();
            routeStop.setRoute(route);
            routeStop.setAddress(address);
            // entra sempre no fim; pra mudar a posicao o motorista faz a troca
            routeStop.setOrderIndex(routeStopRepository.findByRouteIdOrderByOrderIndexAsc(routeId).size() + 1);
            response.add(toResponse(routeStopRepository.save(routeStop)));
        }
        return response;
    }

    @Transactional
    public RouteStopResponse updateRouteStopOrder(UUID routeStopId, UpdateRouteStopOrderRequest request, Authentication authentication) {
        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("Route stop not found"));
        checkOwnership(routeStop.getRoute(), authentication);
        // troca de lugar com o ponto que esta na posicao pedida; o resto nao muda
        RouteStop other = routeStopRepository
                .findFirstByRouteIdAndOrderIndex(routeStop.getRoute().getId(), request.orderIndex())
                .orElseThrow(() -> new BadRequestException("Não existe ponto nessa posição"));
        other.setOrderIndex(routeStop.getOrderIndex());
        routeStop.setOrderIndex(request.orderIndex());
        // a troca nao pode deixar o desembarque de algum aluno antes do embarque
        UUID routeId = routeStop.getRoute().getId();
        Stream.concat(studentStopRepository.findByStopId(routeStop.getId()).stream(),
                        studentStopRepository.findByStopId(other.getId()).stream())
                .map(studentStop -> studentStop.getStudent().getId())
                .distinct()
                .forEach(studentId -> checkBoardingBeforeAlighting(
                        studentStopRepository.findByStudentIdAndStopRouteId(studentId, routeId)));
        return toResponse(routeStop);
    }

    // em cada dia o embarque do aluno tem que vir antes do desembarque na ordem dos pontos da rota
    public void checkBoardingBeforeAlighting(List<StudentStop> stopsOfStudent) {
        for (StudentStop boarding : stopsOfStudent) {
            if (boarding.getAction() != StudentStopAction.BOARDING) continue;
            for (StudentStop alighting : stopsOfStudent) {
                if (alighting.getAction() == StudentStopAction.ALIGHTING
                        && !Collections.disjoint(boarding.getWeekdays(), alighting.getWeekdays())
                        && boarding.getStop().getOrderIndex() >= alighting.getStop().getOrderIndex()) {
                    throw new BadRequestException("O embarque de " + boarding.getStudent().getName()
                            + " precisa vir antes do desembarque na ordem da rota");
                }
            }
        }
    }

    // so exclui ponto vazio; os pontos de baixo sobem uma posicao pra nao deixar buraco na ordem
    @Transactional
    public void deleteRouteStop(UUID routeStopId, Authentication authentication) {
        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("Route stop not found"));
        checkOwnership(routeStop.getRoute(), authentication);
        if (studentStopRepository.existsByStopId(routeStopId) || schoolStopRepository.existsByStopId(routeStopId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remova os alunos e escolas do ponto antes de excluir");
        }
        routeStopRepository.delete(routeStop);
        for (RouteStop stop : routeStopRepository.findByRouteIdOrderByOrderIndexAsc(routeStop.getRoute().getId())) {
            if (!stop.getId().equals(routeStopId) && stop.getOrderIndex() > routeStop.getOrderIndex()) {
                stop.setOrderIndex(stop.getOrderIndex() - 1);
            }
        }
    }

    private RouteStopResponse toResponse(RouteStop routeStop) {
        Address address = routeStop.getAddress();
        return new RouteStopResponse(
                routeStop.getId(),
                routeStop.getOrderIndex(),
                new AddressResponseDTO(
                        address.getId(),
                        address.getStreet(),
                        address.getZipCode(),
                        address.getCity(),
                        address.getNeighborhood(),
                        address.getNumber(),
                        address.getState(),
                        address.getLatitude(),
                        address.getLongitude()
                ),
                studentStopRepository.findByStopId(routeStop.getId()).stream()
                        .map(StudentStopService::toResponse)
                        .toList(),
                schoolStopRepository.findByStopId(routeStop.getId()).stream()
                        .map(schoolStop -> new SchoolStopResponseDTO(schoolStop.getId(), schoolStop.getSchool().getId(), routeStop.getId()))
                        .toList()
        );
    }

    public List<RoutePendingResponse> findPending(UUID routeId, Authentication authentication) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));
        checkOwnership(route, authentication);
        return pendingOf(routeId);
    }

    // para cada aluno: dias em que ele aparece na rota menos os dias em que tem embarque (e desembarque).
    // a trip usa isso pra nao iniciar com a configuracao incompleta
    public List<RoutePendingResponse> pendingOf(UUID routeId) {
        Map<Student, List<StudentStop>> byStudent = studentStopRepository.findByRouteWithStudent(routeId).stream()
                .collect(Collectors.groupingBy(StudentStop::getStudent));
        List<RoutePendingResponse> pending = new ArrayList<>();
        byStudent.forEach((student, stops) -> {
            Set<WeekDay> configured = EnumSet.noneOf(WeekDay.class);
            Set<WeekDay> boarding = EnumSet.noneOf(WeekDay.class);
            Set<WeekDay> alighting = EnumSet.noneOf(WeekDay.class);
            for (StudentStop stop : stops) {
                configured.addAll(stop.getWeekdays());
                (stop.getAction() == StudentStopAction.BOARDING ? boarding : alighting).addAll(stop.getWeekdays());
            }
            Set<WeekDay> missingBoarding = EnumSet.copyOf(configured);
            missingBoarding.removeAll(boarding);
            Set<WeekDay> missingAlighting = EnumSet.copyOf(configured);
            missingAlighting.removeAll(alighting);
            if (!missingBoarding.isEmpty() || !missingAlighting.isEmpty()) {
                pending.add(new RoutePendingResponse(student.getId(), student.getName(), missingBoarding, missingAlighting));
            }
        });
        return pending;
    }

    // van padrao da rota: usada quando o motorista nao escolhe outra ao iniciar a trip
    @Transactional
    public RouteResponse setDefaultVehicle(UUID routeId, UUID vehicleId, Authentication authentication) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));
        Driver driver = checkOwnership(route, authentication);
        if (driverVehicleRepository.findFirstByDriverUserIdAndVehicleIdAndEndDateIsNull(driver.getUserId(), vehicleId).isEmpty()) {
            throw new BadRequestException("Essa van não está vinculada a você");
        }
        route.setVehicle(vehicleRepository.getReferenceById(vehicleId));
        return new RouteResponse(route.getId(), route.getName(), driver.getUser().getName());
    }

    public RouteResponse findById(UUID id, Authentication authentication) {
        Driver driver = driverRepository.findById(AuthenticatedUser.id(authentication))
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));
        checkOwnership(route, authentication);
        return new RouteResponse(
                route.getId(),
                route.getName(),
                driver.getUser().getName()
        );
    }
    public List<RouteStopResponse> findAllRouteStops(
            UUID routeId,
            Authentication authentication
    ) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new EntityNotFoundException("Route not found"));

        checkOwnership(route, authentication);

        return routeStopRepository.findByRouteIdOrderByOrderIndexAsc(routeId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RouteWithStopsResponse> findAll(Authentication authentication) {

        Driver driver = driverRepository.findById(AuthenticatedUser.id(authentication))
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));

        List<Route> routes = routeDriverRepository.findByDriverUserId(driver.getUserId())
                .stream()
                .map(RouteDriver::getRoute)
                .toList();

        List<RouteWithStopsResponse> response = new ArrayList<>();

        for (Route route : routes) {

            List<RouteStopResponse> stops = routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId()).stream()
                    .map(this::toResponse)
                    .toList();

            response.add(new RouteWithStopsResponse(
                    route.getId(),
                    route.getName(),
                    driver.getUser().getName(),
                    stops
            ));
        }

        return response;
    }

    // so o motorista vinculado a rota (route_drivers) pode le-la ou altera-la
    public Driver checkOwnership(Route route, Authentication authentication) {
        Driver driver = driverRepository.findById(AuthenticatedUser.id(authentication))
                .orElseThrow(() -> new AccessDeniedException("Driver not found"));
        if (!routeDriverRepository.existsByRouteIdAndDriverUserId(route.getId(), driver.getUserId())) {
            throw new AccessDeniedException("Route does not belong to this driver");
        }
        return driver;
    }
}
