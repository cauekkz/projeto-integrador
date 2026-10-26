package br.com.vanroute.backend.dtos.notification;

import br.com.vanroute.backend.models.notification.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

// payload ja vem como objeto JSON (ex.: {"tripId": "..."}), sem precisar de JSON.parse no front
public record NotificationResponseDTO(
        UUID id,
        NotificationType type,
        String title,
        String message,
        Map<String, Object> payload,
        LocalDateTime createdAt,
        boolean read
) {
}
