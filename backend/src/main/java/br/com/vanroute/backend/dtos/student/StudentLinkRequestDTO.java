package br.com.vanroute.backend.dtos.student;

import java.util.UUID;

import br.com.vanroute.backend.models.student.enums.RelationType;
import jakarta.validation.constraints.NotBlank;                                                     

public record  StudentLinkRequestDTO(
    @NotBlank(message = "O id é obrigatório")
    UUID id,
    @NotBlank(message = "O tipo de relação é obrigatório")
    RelationType relationType


) {}                            