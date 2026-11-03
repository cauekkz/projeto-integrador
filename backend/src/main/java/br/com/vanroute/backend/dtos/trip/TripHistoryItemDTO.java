package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

// uma linha por aluno por viagem: quando embarcou, quando chegou ou se faltou.
// occurrences = ocorrencias da viagem (o detalhe vem em GET /api/trip/{tripId}/occurrences)
public record TripHistoryItemDTO(
        UUID tripId,
        LocalDate date,
        TripStatus tripStatus,
        LocalTime startTime,
        LocalTime endTime,
        UUID routeId,
        String routeName,
        UUID driverId,
        String driverName,
        UUID studentId,
        String studentName,
        BoardingStatus boardingStatus,
        LocalDateTime boardingTime,
        LocalDateTime unboardingTime,
        long occurrences
) {
    public TripHistoryItemDTO withOccurrences(long count) {
        return new TripHistoryItemDTO(tripId, date, tripStatus, startTime, endTime, routeId, routeName, driverId, driverName,
                studentId, studentName, boardingStatus, boardingTime, unboardingTime, count);
    }
}
