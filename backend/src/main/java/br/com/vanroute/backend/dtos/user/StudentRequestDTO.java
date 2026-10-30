package br.com.vanroute.backend.dtos.user;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.dtos.student.StudentAddressRequestDTO;
import br.com.vanroute.backend.models.student.enums.RelationType;
import jakarta.validation.Valid;
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
    RelationType relationType,

    // onde o aluno e buscado (vira o endereco "Casa" dele): mande address (novo) OU addressId
    // (reaproveita o endereco de um irmao ou o seu de cobranca, sem digitar de novo)
    @Valid
    AddressRequestDTO address,

    UUID addressId,

    @Size(max = 60, message = "O apelido do endereço deve ter no máximo 60 caracteres")
    String addressLabel,

    // ou varios de uma vez: [{label, address | addressId}]; o primeiro sem apelido vira "Casa"
    @Valid
    List<StudentAddressRequestDTO> addresses

) {}
