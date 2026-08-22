package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

public record RouteResponse(
        UUID id,
        String name
) {
}