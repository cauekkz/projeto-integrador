package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.route.stops.AddStudentToRouteRequest;
import br.com.vanroute.backend.dtos.route.RouteStopOptionsDTO;
import br.com.vanroute.backend.dtos.route.*;
import br.com.vanroute.backend.dtos.route.stops.MoveStudentStopRequestDTO;
import br.com.vanroute.backend.dtos.route.stops.UpdateWeekdaysRequestDTO;
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
    private final StudentStopService studentStopService;
    private final SchoolStopService schoolStopService;

    public RouteController(RouteService routeService,
                           StudentStopService studentStopService, SchoolStopService schoolStopService) {
        this.routeService = routeService;
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

    @PutMapping("/stop/{routeStopId}/swap-order")
    public ResponseEntity<RouteEditResponse> updateRouteStopOrder(
            @PathVariable UUID routeStopId,
            @RequestBody @Valid UpdateRouteStopOrderRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(routeService.updateRouteStopOrder(routeStopId, request, authentication));
    }

    @PutMapping("/student-stop/{studentStopId}/move")
    public RouteEditResponse moveStudentStop(@PathVariable UUID studentStopId,
                                                  @Valid @RequestBody MoveStudentStopRequestDTO request,
                                                  Authentication authentication) {
        return studentStopService.moveStudentStop(studentStopId, request.routeStopId(), authentication);
    }

    @PutMapping("/student-stop/{studentStopId}/weekdays")
    public RouteEditResponse updateWeekdays(@PathVariable UUID studentStopId,
                                                 @RequestBody UpdateWeekdaysRequestDTO request,
                                                 Authentication authentication) {
        return studentStopService.updateWeekdays(studentStopId, request.weekdays(), authentication);
    }

    @DeleteMapping("/student-stop/{studentStopId}")
    public RouteEditResponse deleteStudentStop(@PathVariable UUID studentStopId, Authentication authentication) {
        return studentStopService.deleteStudentStop(studentStopId, authentication);
    }

    @DeleteMapping("/school-stop/{schoolStopId}")
    public RouteEditResponse deleteSchoolStop(@PathVariable UUID schoolStopId, Authentication authentication) {
        return schoolStopService.deleteSchoolStop(schoolStopId, authentication);
    }

    @DeleteMapping("/stop/{routeStopId}")
    public RouteEditResponse deleteRouteStop(@PathVariable UUID routeStopId, Authentication authentication) {
        return routeService.deleteRouteStop(routeStopId, authentication);
    }

    // van padrao da rota (a trip usa ela quando o motorista nao escolhe outra)
    @PutMapping("/{routeId}/set-vehicle/{vehicleId}")
    public ResponseEntity<RouteResponse> setDefaultVehicle(@PathVariable UUID routeId, @PathVariable UUID vehicleId,
                                                           Authentication authentication) {
        return ResponseEntity.ok(routeService.setDefaultVehicle(routeId, vehicleId, authentication));
    }

    // "adicionar aluno na rota" num passo so: usa/cria o ponto do endereco (na ordem certa), garante a escola do
    // contrato na rota e liga o aluno. Devolve os pontos atualizados e as pendencias
    @PostMapping("/{routeId}/add-student/{studentId}")
    public RouteEditResponse addStudentToRoute(@PathVariable UUID routeId, @PathVariable UUID studentId,
                                                     @RequestBody @Valid AddStudentToRouteRequest request,
                                                     Authentication authentication) {
        return studentStopService.addStudentToRoute(routeId, studentId, request, authentication);
    }

    // quem o motorista pode pôr na rota: alunos com contrato ativo e os enderecos de cada um (o studentAddressId)
    @GetMapping("/stop-options")
    public RouteStopOptionsDTO stopOptions(Authentication authentication) {
        return routeService.stopOptions(authentication);
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
