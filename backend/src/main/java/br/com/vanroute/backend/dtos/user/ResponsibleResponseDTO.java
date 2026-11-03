package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;

import java.util.List;

public record ResponsibleResponseDTO(
        String name,
        String email,
        String cpf,
        String phone,
        FinancialStatus financialStatus,
        List<String> roles,
        AddressResponseDTO address
) {
}
