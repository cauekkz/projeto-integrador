package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.route.*;
import br.com.vanroute.backend.dtos.route.stops.MoveStudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.SchoolStopResponseDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.StudentStopResponseDTO;
import br.com.vanroute.backend.dtos.route.stops.UpdateWeekdaysRequestDTO;
import br.com.vanroute.backend.services.MapService;
import br.com.vanroute.backend.services.SchoolStopService;
import br.com.vanroute.backend.services.StudentStopService;
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
    private final MapService mapService;
    private final StudentStopService studentStopService;
    private final SchoolStopService schoolStopService;

    public RouteController(RouteService routeService, MapService mapService,
                           StudentStopService studentStopService, SchoolStopService schoolStopService) {
        this.routeService = routeService;
        this.mapService = mapService;
        this.studentStopService = studentStopService;
        this.schoolStopService = schoolStopService;
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
            @RequestBody List<@Valid AddRouteStopRequest> requests,
            Authentication authentication
    ) {
        List<RouteStopResponse> response = routeService.addRouteStop(
                routeId,
                requests,
                authentication
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/stop/{routeStopId}/swap-order")
    public ResponseEntity<RouteStopResponse> updateRouteStopOrder(
            @PathVariable UUID routeStopId,
            @RequestBody @Valid UpdateRouteStopOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(routeService.updateRouteStopOrder(routeStopId, request, authentication));
    }

    @PostMapping("/stop/{routeStopId}/add-student/{studentId}")
    public StudentStopResponseDTO createStudentStop(@PathVariable UUID routeStopId, @PathVariable UUID studentId,
                                                    @Valid @RequestBody StudentStopRequestDTO studentStopRequestDTO,
                                                    Authentication authentication) {
        return studentStopService.createStudentStop(studentStopRequestDTO, studentId, routeStopId, authentication);
    }

    @PostMapping("/stop/{routeStopId}/add-school/{schoolId}")
    public SchoolStopResponseDTO createSchoolStop(@PathVariable UUID routeStopId, @PathVariable UUID schoolId,
                                                  Authentication authentication) {
        return schoolStopService.addSchoolStop(schoolId, routeStopId, authentication);
    }

    @PutMapping("/student-stop/{studentStopId}/move")
    public StudentStopResponseDTO moveStudentStop(@PathVariable UUID studentStopId,
                                                  @Valid @RequestBody MoveStudentStopRequestDTO request,
                                                  Authentication authentication) {
        return studentStopService.moveStudentStop(studentStopId, request.routeStopId(), authentication);
    }

    @PutMapping("/student-stop/{studentStopId}/weekdays")
    public StudentStopResponseDTO updateWeekdays(@PathVariable UUID studentStopId,
                                                 @RequestBody UpdateWeekdaysRequestDTO request,
                                                 Authentication authentication) {
        return studentStopService.updateWeekdays(studentStopId, request.weekdays(), authentication);
    }

    @DeleteMapping("/student-stop/{studentStopId}")
    public ResponseEntity<Void> deleteStudentStop(@PathVariable UUID studentStopId, Authentication authentication) {
        studentStopService.deleteStudentStop(studentStopId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/school-stop/{schoolStopId}")
    public ResponseEntity<Void> deleteSchoolStop(@PathVariable UUID schoolStopId, Authentication authentication) {
        schoolStopService.deleteSchoolStop(schoolStopId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/stop/{routeStopId}")
    public ResponseEntity<Void> deleteRouteStop(@PathVariable UUID routeStopId, Authentication authentication) {
        routeService.deleteRouteStop(routeStopId, authentication);
        return ResponseEntity.noContent().build();
    }

    // van padrao da rota (a trip usa ela quando o motorista nao escolhe outra)
    @PutMapping("/{routeId}/set-vehicle/{vehicleId}")
    public ResponseEntity<RouteResponse> setDefaultVehicle(@PathVariable UUID routeId, @PathVariable UUID vehicleId,
                                                           Authentication authentication) {
        return ResponseEntity.ok(routeService.setDefaultVehicle(routeId, vehicleId, authentication));
    }

    @GetMapping("/{routeId}/pending")
    public List<RoutePendingResponse> findPending(@PathVariable UUID routeId, Authentication authentication) {
        return routeService.findPending(routeId, authentication);
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
