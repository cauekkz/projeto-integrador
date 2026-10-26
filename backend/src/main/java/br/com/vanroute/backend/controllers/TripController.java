package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.trip.*;
import br.com.vanroute.backend.services.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// fushiguro
// tempo real (GPS do motorista, mapa e tempo ate o ponto pros pais) e pelo WebSocket /trip/{tripId}
@RestController
@RequestMapping("/api/trip")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // botao "iniciar rota"; restart=true cancela a trip de hoje e comeca do zero
    @PostMapping("/start/{routeId}")
    public ResponseEntity<TripResponseDTO> start(@PathVariable UUID routeId,
                                                 @RequestParam(defaultValue = "false") boolean restart,
                                                 @RequestBody @Valid TripStartRequestDTO request,
                                                 Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tripService.start(routeId, request, restart, AuthenticatedUser.id(authentication)));
    }

    @GetMapping("/route/{routeId}/today")
    public TripResponseDTO todayTrip(@PathVariable UUID routeId, Authentication authentication) {
        return tripService.todayTrip(routeId, AuthenticatedUser.id(authentication));
    }

    @GetMapping("/{tripId}")
    public TripResponseDTO getTrip(@PathVariable UUID tripId, Authentication authentication) {
        return tripService.getTrip(tripId, AuthenticatedUser.id(authentication));
    }

    @PutMapping("/{tripId}/student/{studentId}/board")
    public TripStudentDTO board(@PathVariable UUID tripId, @PathVariable UUID studentId, Authentication authentication) {
        return tripService.board(tripId, studentId, AuthenticatedUser.id(authentication));
    }

    @PutMapping("/{tripId}/student/{studentId}/alight")
    public TripStudentDTO alight(@PathVariable UUID tripId, @PathVariable UUID studentId, Authentication authentication) {
        return tripService.alight(tripId, studentId, AuthenticatedUser.id(authentication));
    }

    @PutMapping("/{tripId}/student/{studentId}/absent")
    public TripResponseDTO markAbsent(@PathVariable UUID tripId, @PathVariable UUID studentId, Authentication authentication) {
        return tripService.markAbsent(tripId, studentId, AuthenticatedUser.id(authentication));
    }

    @PostMapping("/{tripId}/finish")
    public TripResponseDTO finish(@PathVariable UUID tripId, Authentication authentication) {
        return tripService.finish(tripId, AuthenticatedUser.id(authentication));
    }
}
