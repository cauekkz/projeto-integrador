package br.com.vanroute.backend.dtos.route.stops;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveStudentStopRequestDTO(
        @NotNull(message = "O ponto é obrigatório")
        UUID routeStopId
) {
}
