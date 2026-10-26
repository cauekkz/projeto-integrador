package br.com.vanroute.backend.dtos.trip;

import br.com.vanroute.backend.models.student.enums.StudentStopAction;
import br.com.vanroute.backend.models.student.enums.StudentStopLocationType;
import br.com.vanroute.backend.models.trip.enums.BoardingStatus;

import java.time.LocalDateTime;
import java.util.UUID;

// boardedAt/deliveredAt: quando embarcou e quando foi entregue (null enquanto nao aconteceu)
public record TripStudentDTO(
        UUID studentId,
        String studentName,
        StudentStopAction action,
        StudentStopLocationType locationType,
        BoardingStatus status,
        LocalDateTime boardedAt,
        LocalDateTime deliveredAt
) {
}
