package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.trip.enums.OccurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OccurrenceRequestDTO(
        @NotNull(message = "O tipo da ocorrência é obrigatório")
        OccurrenceType type,
        @NotBlank(message = "Descreva a ocorrência")
        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres")
        String description
) {
}
