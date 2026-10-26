package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;

import java.util.UUID;

// trips de hoje em que o filho esta (o responsavel usa o tripId no tracking)
public record StudentTripTodayDTO(
        UUID tripId,
        UUID routeId,
        String routeName,
        TripStatus tripStatus,
        BoardingStatus boardingStatus
) {
}
