package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.BoardingStatus;
import br.com.vanroute.backend.models.trip.enums.TripStatus;

import java.util.UUID;

// trips de hoje em que o filho esta (o responsavel usa o tripId no tracking), com o motorista e a van dessa viagem
// pro card do acompanhamento. driverPhotoKey: GET /api/storage/download-url; driverRating null sem avaliacoes
public record StudentTripTodayDTO(
        UUID tripId,
        UUID routeId,
        String routeName,
        TripStatus tripStatus,
        BoardingStatus boardingStatus,
        UUID driverId,
        String driverName,
        String driverPhotoKey,
        Double driverRating,
        String vehiclePlate,
        String vehicleModel,
        String vehicleColor
) {
}
