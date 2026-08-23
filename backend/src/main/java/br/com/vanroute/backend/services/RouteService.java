package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.*;
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
import java.util.UUID;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final AddressRepository addressRepository;
    private final RouteStopRepository routeStopRepository;
    private final DriverRepository driverRepository;
    private final RouteDriverRepository routeDriverRepository;


    public RouteService(RouteRepository routeRepository, AddressRepository addressRepository, RouteStopRepository routeStopRepository,
    DriverRepository driverRepository, RouteDriverRepository routeDriverRepository) {
        this.routeRepository = routeRepository;
        this.addressRepository = addressRepository;
        this.routeStopRepository = routeStopRepository;
        this.driverRepository = driverRepository;
        this.routeDriverRepository = routeDriverRepository;
    }

    public RouteResponse createRoute(CreateRouteRequestDTO createRouteRequestDTO, Authentication authentication) {

        Route route = new Route();
        route.setName(createRouteRequestDTO.name());
        Route routeSaved = routeRepository.save(route);
        String cpf = authentication.getName();
        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
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

    public List<RouteStopResponse> addRouteStop(UUID routeId, List<AddRouteStopRequest> addRouteStopRequest, Authentication authentication) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route doesn't exists"));
        String cpf = authentication.getName();
        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        List<RouteStopResponse> response = new ArrayList<>();
        for (AddRouteStopRequest request : addRouteStopRequest) {
            Address address = addressRepository.findById(request.studentAddressId())
                    .orElseThrow(() -> new RuntimeException("Student address not found"));
            RouteStop routeStop = new RouteStop();
            routeStop.setRoute(route);
            routeStop.setAddress(address);
            routeStop.setOrderIndex(request.orderIndex());
            RouteStop routeSaved = routeStopRepository.save(routeStop);

            response.add(new RouteStopResponse(
                    routeSaved.getId(),
                    routeSaved.getOrderIndex(),
                    new AddressResponseDTO(
                            address.getId(),
                            address.getStreet(),
                            address.getZipCode(),
                            address.getCity(),
                            address.getNeighborhood(),
                            address.getNumber(),
                            address.getStreet(),
                            address.getLatitude(),
                            address.getLongitude()
                    )
            ));
        }
        return response;
    }

    public RouteResponse findById(UUID id, Authentication authentication) {
        String cpf = authentication.getName();
        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found"));
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
                .orElseThrow(() -> new RuntimeException("Route not found"));

        String cpf = authentication.getName();

        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        List<RouteStop> routeStops =
                routeStopRepository.findByRouteIdOrderByOrderIndexAsc(routeId);

        List<RouteStopResponse> response = new ArrayList<>();

        for (RouteStop routeStop : routeStops) {

            Address address = routeStop.getAddress();

            response.add(new RouteStopResponse(
                    routeStop.getId(),
                    routeStop.getOrderIndex(),
                    new AddressResponseDTO(
                            address.getId(),
                            address.getStreet(),
                            address.getZipCode(),
                            address.getCity(),
                            address.getNeighborhood(),
                            address.getNumber(),
                            address.getStreet(),
                            address.getLatitude(),
                            address.getLongitude()
                    )
            ));
        }

        return response;
    }

    public List<RouteWithStopsResponse> findAll(Authentication authentication) {

        String cpf = authentication.getName();

        Driver driver = driverRepository.findByUserCpf(cpf)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        List<Route> routes = routeRepository.findAll();

        List<RouteWithStopsResponse> response = new ArrayList<>();

        for (Route route : routes) {

            List<RouteStop> routeStops =
                    routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId());

            List<RouteStopResponse> stops = new ArrayList<>();

            for (RouteStop routeStop : routeStops) {

                Address address = routeStop.getAddress();

                stops.add(new RouteStopResponse(
                        routeStop.getId(),
                        routeStop.getOrderIndex(),
                        new AddressResponseDTO(
                                address.getId(),
                                address.getStreet(),
                                address.getZipCode(),
                                address.getCity(),
                                address.getNeighborhood(),
                                address.getNumber(),
                                address.getStreet(),
                                address.getLatitude(),
                                address.getLongitude()
                        )
                ));
            }

            response.add(new RouteWithStopsResponse(
                    route.getId(),
                    route.getName(),
                    driver.getUser().getName(),
                    stops
            ));
        }

        return response;
    }
}
