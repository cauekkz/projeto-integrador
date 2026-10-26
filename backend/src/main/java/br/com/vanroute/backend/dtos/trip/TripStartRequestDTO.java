package br.com.vanroute.backend.dtos.trip;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// posicao atual do motorista (GPS do celular); van opcional: sem ela usa a van padrao da rota
public record TripStartRequestDTO(
        @NotNull(message = "A latitude é obrigatória")
        Double latitude,
        @NotNull(message = "A longitude é obrigatória")
        Double longitude,
        UUID vehicleId
) {
}
