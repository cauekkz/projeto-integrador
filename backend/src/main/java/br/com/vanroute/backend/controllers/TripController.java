package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.config.AuthenticatedUser;
import br.com.vanroute.backend.dtos.trip.*;
import br.com.vanroute.backend.services.OccurrenceService;
import br.com.vanroute.backend.services.TripHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import br.com.vanroute.backend.services.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// fushiguro
// tempo real (GPS do motorista, mapa e tempo ate o ponto pros pais) e pelo WebSocket /trip/{tripId}
@RestController
@RequestMapping("/api/trip")
public class TripController {

    private final TripService tripService;
    private final OccurrenceService occurrenceService;
    private final TripHistoryService tripHistoryService;

    public TripController(TripService tripService, OccurrenceService occurrenceService, TripHistoryService tripHistoryService) {
        this.occurrenceService = occurrenceService;
        this.tripHistoryService = tripHistoryService;
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

    // historico por aluno (responsavel: dos filhos; motorista: das viagens dele). Filtros opcionais:
    // studentId, driverId, routeId, date (um dia) ou from/to (periodo, AAAA-MM-DD)
    @GetMapping("/history")
    public Page<TripHistoryItemDTO> history(@RequestParam(required = false) UUID studentId,
                                            @RequestParam(required = false) UUID driverId,
                                            @RequestParam(required = false) UUID routeId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @PageableDefault(size = 20) Pageable pageable,
                                            Authentication authentication) {
        return tripHistoryService.list(AuthenticatedUser.id(authentication), new TripHistoryService.Filter(studentId, driverId, routeId,
                date != null ? date : from, date != null ? date : to), pageable);
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

    // ocorrencia na viagem (motorista): os pais dos alunos da viagem recebem na hora
    @PostMapping("/{tripId}/occurrences")
    public ResponseEntity<OccurrenceResponseDTO> reportOccurrence(@PathVariable UUID tripId, @RequestBody @Valid OccurrenceRequestDTO request,
                                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(occurrenceService.report(tripId, request, AuthenticatedUser.id(authentication)));
    }

    // ocorrencias da viagem (motorista ou pai de aluno dela), a mais nova primeiro
    @GetMapping("/{tripId}/occurrences")
    public List<OccurrenceResponseDTO> occurrences(@PathVariable UUID tripId, Authentication authentication) {
        return occurrenceService.list(tripId, AuthenticatedUser.id(authentication));
    }
}
