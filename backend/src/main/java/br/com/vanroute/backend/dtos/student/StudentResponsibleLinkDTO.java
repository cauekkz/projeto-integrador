package br.com.vanroute.backend.dtos.student;

import br.com.vanroute.backend.models.student.enums.RelationType;

import java.util.UUID;

// um responsavel do aluno: nome, rotulo do vinculo e se e o admin (sem telefone)
public record StudentResponsibleLinkDTO(UUID responsibleId, String name, RelationType relationType, boolean admin) {
}
