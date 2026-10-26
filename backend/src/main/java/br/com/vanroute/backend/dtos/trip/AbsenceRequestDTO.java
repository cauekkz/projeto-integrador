package br.com.vanroute.backend.dtos.trip;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record AbsenceRequestDTO(
        @NotNull(message = "A rota é obrigatória")
        UUID routeId,
        @NotNull(message = "A data é obrigatória")
        LocalDate date
) {
}
