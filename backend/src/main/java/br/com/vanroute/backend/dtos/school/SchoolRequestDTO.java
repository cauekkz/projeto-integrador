package br.com.vanroute.backend.dtos.school;

import br.com.vanroute.backend.dtos.student.AddressRequestDTO;

public record SchoolRequestDTO(
        String name,
        String phone,
        String email,
        AddressRequestDTO addressRequestDTO
) {
}
