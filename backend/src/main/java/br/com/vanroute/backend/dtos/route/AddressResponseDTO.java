package br.com.vanroute.backend.dtos.route;

import java.util.UUID;

public record AddressResponseDTO(
        UUID id,
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
