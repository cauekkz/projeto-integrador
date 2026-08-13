package br.com.vanroute.backend.dtos.user;

import java.time.LocalDate;

import br.com.vanroute.backend.models.student.enums.RelationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record StudentRequestDTO(
    @NotBlank(message = "O nome é obrigatório")
    String name,

    @Size(max = 700, message = "As observações devem ter no máximo 700 caracteres")
    String notes,

    @Past(message = "A data de nascimento deve ser anterior à data atual")
    @NotNull(message = "A data de nascimento é obrigatória")
    LocalDate birthDate,

    @NotNull(message = "O tipo de relação é obrigatório")
    RelationType relationType
) {}