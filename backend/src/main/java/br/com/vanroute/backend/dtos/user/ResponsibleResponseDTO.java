package br.com.vanroute.backend.dtos.user;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;

import java.util.List;

public record ResponsibleResponseDTO(
        String name,
        String email,
        String cpf,
        String phone,
        List<String> roles,
        AddressResponseDTO address
) {
}
