package br.com.vanroute.backend.dtos.user.token;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(
        @NotBlank(message = "O refreshToken é obrigatório")
        String refreshToken
) {
}
