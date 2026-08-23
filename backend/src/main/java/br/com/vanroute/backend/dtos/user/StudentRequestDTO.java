package br.com.vanroute.backend.dtos.user;

import java.time.LocalDate;

import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
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
    @Size(max = 100, message = "Os dias da semana devem ter no máximo 100 caracteres")
    String weekdays,

    @Past(message = "A data de nascimento deve ser anterior à data atual")
    @NotNull(message = "A data de nascimento é obrigatória")
    LocalDate birthDate,

    @NotNull(message = "O tipo de relação é obrigatório")
    RelationType relationType,

    @NotNull(message = "O endereço é obrigatório")
    AddressRequestDTO address

) {}