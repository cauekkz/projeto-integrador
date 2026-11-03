package br.com.vanroute.backend.dtos.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverGroupRequestDTO(
        @NotBlank(message = "O nome do grupo é obrigatório")
        @Size(max = 60, message = "O nome do grupo deve ter no máximo 60 caracteres")
        String name
) {
}
