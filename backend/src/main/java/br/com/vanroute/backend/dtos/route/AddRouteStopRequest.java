package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

public record AddRouteStopRequest(
        UUID studentAddressId,
        Integer orderIndex
) {
}
