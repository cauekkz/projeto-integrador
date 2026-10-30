package br.com.vanroute.backend.dtos.student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// novo endereco onde o aluno e buscado/deixado. Mande "address" (endereco novo) OU "addressId"
// (reaproveita um endereco que ja existe: de um irmao ou o seu de cobranca). label: "Casa", "Casa da avo"...
public record StudentAddressRequestDTO(
        @Size(max = 60, message = "O apelido deve ter no máximo 60 caracteres")
        String label,
        @Valid
        AddressRequestDTO address,
        UUID addressId
) {
}
