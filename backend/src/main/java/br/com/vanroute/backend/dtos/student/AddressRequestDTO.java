package br.com.vanroute.backend.dtos.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequestDTO(
        @NotBlank(message = "A rua é obrigatória")
        String street,

        @NotBlank(message = "O CEP é obrigatório")
        @Size(max = 20, message = "O CEP deve ter no máximo 20 caracteres")
        String zipCode,

        @NotBlank(message = "A cidade é obrigatória")
        @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres")
        String city,

        @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
        String neighborhood,

        Integer number,

        @Size(max = 50, message = "O estado deve ter no máximo 50 caracteres")
        String state
) {}