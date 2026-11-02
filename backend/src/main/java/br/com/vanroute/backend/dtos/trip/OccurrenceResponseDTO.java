package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.OccurrenceType;

import java.time.LocalDateTime;
import java.util.UUID;

public record OccurrenceResponseDTO(
        UUID id,
        UUID tripId,
        OccurrenceType type,
        String description,
        LocalDateTime dateTime,
        String driverName
) {
}
