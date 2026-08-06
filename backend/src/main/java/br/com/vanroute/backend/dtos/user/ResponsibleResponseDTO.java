package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;

import java.util.Set;

public record ResponsibleResponseDTO(String name, String email, String cpf, String phone, FinancialStatus financialStatus, Set<RolesEntity> roleTypeEnum) {
}
