package br.com.vanroute.backend.dtos.group;

import java.util.List;
import java.util.UUID;

public record DriverGroupResponseDTO(
        UUID id,
        String name,
        List<Member> students
) {
    public record Member(UUID studentId, String studentName) {
    }
}
