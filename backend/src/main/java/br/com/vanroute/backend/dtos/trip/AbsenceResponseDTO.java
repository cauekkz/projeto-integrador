package br.com.vanroute.backend.dtos.trip;

import java.time.LocalDate;
import java.util.UUID;

public record AbsenceResponseDTO(
        UUID id,
        UUID studentId,
        UUID routeId,
        String routeName,
        LocalDate date
) {
}
