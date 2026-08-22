package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.route.CreateRouteRequestDTO;
import br.com.vanroute.backend.dtos.route.RouteResponse;
import br.com.vanroute.backend.models.route.Route;
import br.com.vanroute.backend.repositories.RouteRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RouteService {

    private final RouteRepository routeRepository;

    public RouteService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public RouteResponse createRoute(CreateRouteRequestDTO createRouteRequestDTO){
        Route route = new Route();
        route.setName(createRouteRequestDTO.name());
        Route routeSaved = routeRepository.save(route);
        return new RouteResponse(
                routeSaved.getId(),
                routeSaved.getName()
        );
    }

    public RouteResponse findById(UUID id) {

        Route route = routeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Route not found")
                );

        return new RouteResponse(
                route.getId(),
                route.getName()
        );
    }
}

