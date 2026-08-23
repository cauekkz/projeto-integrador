package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

public record AddRouteStop(
        UUID id,
        Integer orderIndex,
        AddressResponseDTO address
) {
}
