package br.com.vanroute.backend.dtos.student;

import java.util.UUID;

public record AddressResponseDTO(
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
