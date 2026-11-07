package br.com.vanroute.backend.dtos.route;

import br.com.vanroute.backend.models.vehicle.Vehicle;

import java.util.UUID;

// van padrao da rota (a trip usa ela quando o motorista nao escolhe outra). null = nenhuma escolhida
public record RouteVehicleResponse(
        UUID id,
        String plate,
        String model,
        String color,
        // lotacao do CRLV (numero de passageiros)
        Integer capacity
) {
    public static RouteVehicleResponse of(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new RouteVehicleResponse(vehicle.getId(), vehicle.getPlate(), vehicle.getModel(),
                vehicle.getColor(), vehicle.getCapacity());
    }
}
