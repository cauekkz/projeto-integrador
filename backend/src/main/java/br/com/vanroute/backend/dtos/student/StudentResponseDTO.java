package br.com.vanroute.backend.dtos.student;

import java.time.LocalDate;
import java.util.UUID;

public record StudentResponseDTO(
        UUID id,
        String name,
        String notes,
        LocalDate birthDate,
        StudentAddressResponseDTO studentAddressResponseDTO
) {
}
