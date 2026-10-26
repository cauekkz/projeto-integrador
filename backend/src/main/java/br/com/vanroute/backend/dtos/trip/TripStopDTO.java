package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;

import java.util.List;
import java.util.UUID;

// ponto da trip de hoje; done = ninguem mais precisa embarcar/desembarcar ali
public record TripStopDTO(
        UUID routeStopId,
        Integer orderIndex,
        AddressResponseDTO address,
        List<TripStudentDTO> students,
        List<UUID> schoolIds,
        boolean done
) {
}
