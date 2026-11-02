package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.TripStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TripResponseDTO(
        UUID id,
        UUID routeId,
        String routeName,
        LocalDate date,
        TripStatus status,
        LocalTime startTime,
        LocalTime endTime,
        UUID vehicleId,
        String vehiclePlate,
        List<TripStopDTO> stops,
        TripPathDTO path
) {
}
