package br.com.vanroute.backend.dtos.student;

import java.util.UUID;

import br.com.vanroute.backend.models.student.enums.RelationType;
import jakarta.validation.constraints.NotNull;                                                     

public record  StudentLinkRequestDTO(
    @NotNull(message = "O id é obrigatório")
    UUID id,
    @NotNull(message = "O tipo de relação é obrigatório")
    RelationType relationType


) {}                            