package br.com.vanroute.backend.dtos.route;

import java.util.List;
import java.util.UUID;

public record RouteWithStopsResponse(
        UUID id,
        String name,
        String driverName,
        // van padrao da rota; null = nenhuma escolhida
        RouteVehicleResponse vehicle,
        List<RouteStopResponse> stops
) {
}
