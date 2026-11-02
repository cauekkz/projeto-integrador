package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.trip.OccurrenceRequestDTO;
import br.com.vanroute.backend.dtos.trip.OccurrenceResponseDTO;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.trip.Occurrence;
import br.com.vanroute.backend.models.trip.Trip;
import br.com.vanroute.backend.models.trip.enums.OccurrenceType;
import br.com.vanroute.backend.models.trip.enums.TripStatus;
import br.com.vanroute.backend.repositories.BoardingRepository;
import br.com.vanroute.backend.repositories.OccurrenceRepository;
import br.com.vanroute.backend.repositories.StudentResponsibleRepository;
import br.com.vanroute.backend.repositories.TripRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

// ocorrencia = aviso do motorista durante a viagem (transito, van quebrou...). Vai na hora pros responsaveis
// de todos os alunos da viagem, pela notificacao (que ja chega em tempo real no WebSocket /notifications)
@Service
public class OccurrenceService {

    private static final Set<TripStatus> CLOSED = EnumSet.of(TripStatus.FINISHED, TripStatus.CANCELED, TripStatus.FAILED);

    private final OccurrenceRepository occurrenceRepository;
    private final TripRepository tripRepository;
    private final BoardingRepository boardingRepository;
    private final StudentResponsibleRepository studentResponsibleRepository;
    private final NotificationService notificationService;

    public OccurrenceService(OccurrenceRepository occurrenceRepository, TripRepository tripRepository,
                             BoardingRepository boardingRepository, StudentResponsibleRepository studentResponsibleRepository,
                             NotificationService notificationService) {
        this.occurrenceRepository = occurrenceRepository;
        this.tripRepository = tripRepository;
        this.boardingRepository = boardingRepository;
        this.studentResponsibleRepository = studentResponsibleRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public OccurrenceResponseDTO report(UUID tripId, OccurrenceRequestDTO request, UUID driverId) {
        Trip trip = trip(tripId);
        if (!trip.getDriver().getUserId().equals(driverId)) {
            throw new AccessDeniedException("Só o motorista da viagem registra ocorrência");
        }
        if (CLOSED.contains(trip.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A viagem já terminou");
        }
        Occurrence occurrence = new Occurrence();
        occurrence.setTrip(trip);
        occurrence.setDriver(trip.getDriver());
        occurrence.setType(request.type());
        occurrence.setDescription(request.description().trim());
        occurrence.setDateTime(LocalDateTime.now(TripService.ZONE));
        occurrence = occurrenceRepository.save(occurrence);

        notificationService.send(responsiblesOf(tripId), NotificationType.OCCURRENCE,
                "Ocorrência na viagem: " + label(request.type()),
                trip.getRoute().getName() + ": " + occurrence.getDescription(),
                Map.of("tripId", tripId, "occurrenceId", occurrence.getId(), "type", request.type().name()));
        return toResponse(occurrence);
    }

    // o motorista da viagem ou um responsavel de aluno dela
    @Transactional(readOnly = true)
    public List<OccurrenceResponseDTO> list(UUID tripId, UUID userId) {
        Trip trip = trip(tripId);
        if (!trip.getDriver().getUserId().equals(userId) && !responsiblesOf(tripId).contains(userId)) {
            throw new AccessDeniedException("Você não faz parte dessa viagem");
        }
        return occurrenceRepository.findByTripIdOrderByDateTimeDesc(tripId).stream().map(OccurrenceService::toResponse).toList();
    }

    private Set<UUID> responsiblesOf(UUID tripId) {
        Set<UUID> responsibles = new LinkedHashSet<>();
        boardingRepository.findByTripWithStudent(tripId).forEach(boarding ->
                responsibles.addAll(studentResponsibleRepository.findResponsibleUserIds(boarding.getStudent().getId())));
        return responsibles;
    }

    private Trip trip(UUID tripId) {
        return tripRepository.findById(tripId).orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada"));
    }

    static String label(OccurrenceType type) {
        return switch (type) {
            case VEHICLE -> "veículo";
            case STUDENT -> "aluno";
            case TRAFFIC -> "trânsito";
            case DISCIPLINARY -> "disciplinar";
            case FINANCIAL -> "financeira";
            case MEDICAL -> "saúde";
            case OTHER -> "outro";
        };
    }

    private static OccurrenceResponseDTO toResponse(Occurrence occurrence) {
        return new OccurrenceResponseDTO(occurrence.getId(), occurrence.getTrip().getId(), occurrence.getType(),
                occurrence.getDescription(), occurrence.getDateTime(), occurrence.getDriver().getUser().getName());
    }
}
