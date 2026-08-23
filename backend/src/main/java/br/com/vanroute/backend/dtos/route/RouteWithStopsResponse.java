package br.com.vanroute.backend.dtos.route;

import java.util.List;
import java.util.UUID;

public record RouteWithStopsResponse(
        UUID id,
        String name,
        String driverName,
        List<RouteStopResponse> stops
) {
}
