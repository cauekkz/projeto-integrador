package br.com.vanroute.backend.dtos.route;

import jakarta.validation.constraints.NotBlank;

public record CreateRouteRequestDTO(
        @NotBlank
        String name
) {
}
