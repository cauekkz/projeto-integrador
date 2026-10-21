package br.com.vanroute.backend.dtos.student;

import java.util.UUID;

public record ChildSummaryResponseDTO(
        UUID id,
        String name
) {
}
