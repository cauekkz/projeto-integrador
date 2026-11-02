package br.com.vanroute.backend.dtos.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RatingRequestDTO(
        @NotNull(message = "A nota é obrigatória")
        @Min(value = 1, message = "A nota vai de 1 a 5")
        @Max(value = 5, message = "A nota vai de 1 a 5")
        Integer score,
        @Size(max = 500, message = "O comentário deve ter no máximo 500 caracteres")
        String comment
) {
}
