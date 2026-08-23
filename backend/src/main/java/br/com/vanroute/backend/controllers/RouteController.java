package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.route.*;
import br.com.vanroute.backend.services.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/route")
public class RouteController {
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/create-route")
    public ResponseEntity<RouteResponse> createRoute(
            @RequestBody @Valid CreateRouteRequestDTO request,
            Authentication authentication
    ) {
        RouteResponse response = routeService.createRoute(
                request,
                authentication
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{routeId}/stops")
    public ResponseEntity<List<RouteStopResponse>> addRouteStops(
            @PathVariable UUID routeId,
            @RequestBody @Valid List<AddRouteStopRequest> requests,
            Authentication authentication
    ) {
        List<RouteStopResponse> response = routeService.addRouteStop(
                routeId,
                requests,
                authentication
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteResponse> findById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        RouteResponse response = routeService.findById(
                id,
                authentication
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{routeId}/stops")
    public ResponseEntity<List<RouteStopResponse>> findAllRouteStops(
            @PathVariable UUID routeId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                routeService.findAllRouteStops(
                        routeId,
                        authentication
                )
        );
    }

    @GetMapping("/get-all-routes-and-stops")
    public ResponseEntity<List<RouteWithStopsResponse>> findAll(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                routeService.findAll(authentication)
        );
    }
}
