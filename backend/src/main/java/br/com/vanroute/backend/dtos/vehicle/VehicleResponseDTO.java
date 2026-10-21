package br.com.vanroute.backend.dtos.vehicle;

import br.com.vanroute.backend.models.vehicle.enums.VehicleStatus;

import java.time.LocalDate;
import java.util.UUID;

public record VehicleResponseDTO(
        UUID id,
        String plate,
        String renavam,
        String chassis,
        String model,
        Integer year,
        Integer manufactureYear,
        String color,
        String fuel,
        String category,
        // lotacao do CRLV (numero de passageiros)
        Integer capacity,
        Integer licensingYear,
        VehicleStatus status,
        // desde quando o motorista usa essa van
        LocalDate linkedSince
) {
}
