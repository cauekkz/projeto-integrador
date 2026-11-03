package br.com.vanroute.backend.dtos.rating;

import java.time.LocalDateTime;

// responsibleName e so o primeiro nome de quem avaliou
public record RatingResponseDTO(
        Integer score,
        String comment,
        LocalDateTime date,
        String responsibleName
) {
}
