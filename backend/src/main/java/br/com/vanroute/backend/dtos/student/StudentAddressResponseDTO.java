package br.com.vanroute.backend.dtos.student;

import java.util.UUID;

public record StudentAddressResponseDTO(
        UUID id,
        String weekdays,
        String street,
        String zipCode,
        String city,
        String neighborhood,
        Integer number,
        String state,
        Double latitude,
        Double longitude
) {
}
