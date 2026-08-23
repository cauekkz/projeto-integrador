package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

public record RouteStopResponse(
        UUID id,
        Integer orderIndex,
        AddressResponseDTO address
) {
}

