package br.com.vanroute.backend.dtos.route;

import jakarta.validation.constraints.NotNull;

public record UpdateRouteStopOrderRequest(
        @NotNull(message = "A ordem é obrigatória")
        Integer orderIndex
) {
}
