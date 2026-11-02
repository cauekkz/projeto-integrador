package br.com.vanroute.backend.dtos.user;

import jakarta.validation.constraints.NotBlank;

public record UpdatePhotoRequestDTO(
        @NotBlank(message = "O arquivo é obrigatório")
        String fileKey
) {
}
