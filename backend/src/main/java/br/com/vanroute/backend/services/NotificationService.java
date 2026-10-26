package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.notification.NotificationResponseDTO;
import br.com.vanroute.backend.models.notification.Notification;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.NotificationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;

// notificacao fica gravada (quem estava fora do app ve depois) e, se o usuario esta conectado
// no WebSocket /notifications, chega na hora pelo Redis "notification-{userId}"
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    // Map simples: serializa igual no HTTP (Jackson 3 do Spring) e no WebSocket
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository, EntityManager entityManager,
                               RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.entityManager = entityManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // uma notificacao pra cada usuario (sem repetir)
    @Transactional
    public void send(Collection<UUID> userIds, NotificationType type, String title, String message, Map<String, ?> payload) {
        String payloadJson = toJson(payload);
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            Notification notification = new Notification();
            notification.setUser(entityManager.getReference(User.class, userId));
            notification.setType(type);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setPayload(payloadJson);
            // flush: o created_at so e preenchido ao gravar, e a mensagem do WebSocket precisa dele
            notification = notificationRepository.saveAndFlush(notification);
            publishAfterCommit(userId, toResponse(notification));
        }
    }

    public Page<NotificationResponseDTO> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(this::toResponse);
    }

    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        // de outro usuario = nao existe pra ele
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Notificação não encontrada"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(TripService.ZONE));
        }
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now(TripService.ZONE));
    }

    public NotificationResponseDTO toResponse(Notification notification) {
        try {
            return new NotificationResponseDTO(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getMessage(),
                    notification.getPayload() == null ? null : objectMapper.readValue(notification.getPayload(), PAYLOAD),
                    notification.getCreatedAt(), notification.getReadAt() != null);
        } catch (Exception e) {
            throw new IllegalStateException("Payload de notificação inválido", e);
        }
    }

    // so publica depois do commit: se a transacao falhar, ninguem recebe notificacao que nao existe
    private void publishAfterCommit(UUID userId, NotificationResponseDTO notification) {
        Runnable publish = () -> {
            try {
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("type", "notification");
                message.put("notification", notification);
                redisTemplate.convertAndSend("notification-" + userId, objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                log.warn("Falha ao publicar notificação do usuário {}", userId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return payload == null ? null : objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload de notificação inválido", e);
        }
    }
}
