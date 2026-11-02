package br.com.vanroute.backend.dtos.route.stops;

import java.util.UUID;

public record SchoolStopResponseDTO(UUID id,
                                    UUID schoolId,
                                    UUID stopId) {
}
