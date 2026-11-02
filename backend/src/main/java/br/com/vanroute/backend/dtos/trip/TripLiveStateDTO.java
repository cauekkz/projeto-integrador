package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.TripStatus;

import java.util.List;
import java.util.UUID;

// estado ao vivo da trip, guardado no Redis: posicao da van, pontos, trajeto e tempos.
// computedAt = quando a API de rotas calculou o trajeto (os tempos descontam a partir dai)
public record TripLiveStateDTO(
        UUID tripId,
        String routeName,
        TripStatus status,
        Double latitude,
        Double longitude,
        Long positionAt,
        Long computedAt,
        Double distance,
        Double duration,
        List<List<Double>> path,
        List<TripLiveStopDTO> stops
) {
}
