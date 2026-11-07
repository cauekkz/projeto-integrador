package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

public record RouteResponse(
        UUID id,
        String name,
        String driverName,
        // van padrao da rota; null = nenhuma escolhida
        RouteVehicleResponse vehicle
) {
}