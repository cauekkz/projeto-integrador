package br.com.vanroute.backend.dtos.school;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;

import java.util.UUID;

// endereco vem junto pro front ja preencher o ponto da escola na rota
public record SchoolResponseDTO(
        UUID id,
        String name,
        String phone,
        String email,
        AddressResponseDTO address
) {
}
