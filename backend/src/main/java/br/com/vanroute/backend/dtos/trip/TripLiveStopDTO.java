package br.com.vanroute.backend.dtos.trip;

import java.util.List;
import java.util.UUID;

// ponto da trip no estado ao vivo; pathIndex = onde o ponto fica na linha do trajeto;
// etaSeconds = tempo ate o ponto no momento do ultimo calculo (null se ja foi atendido)
public record TripLiveStopDTO(
        UUID routeStopId,
        Integer orderIndex,
        Double latitude,
        Double longitude,
        List<TripStudentDTO> students,
        List<UUID> schoolIds,
        boolean done,
        Integer pathIndex,
        Double etaSeconds
) {
}
