package br.com.vanroute.backend.dtos.student;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;

import java.util.UUID;

// um endereco do aluno: id (studentAddressId, o que o motorista usa pra criar o ponto), apelido e o endereco
public record StudentPlaceResponseDTO(
        UUID id,
        String label,
        AddressResponseDTO address
) {
}
