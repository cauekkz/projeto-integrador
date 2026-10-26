package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.notification.Notification;
import br.com.vanroute.backend.models.notification.enums.NotificationType;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.repositories.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private EntityManager entityManager;
    @Mock private RedisTemplate<String, String> redisTemplate;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, entityManager, redisTemplate, new ObjectMapper().findAndRegisterModules());
        when(entityManager.getReference(eq(User.class), any())).thenAnswer(inv -> {
            User u = new User();
            u.setId(inv.getArgument(1));
            return u;
        });
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            n.setCreatedAt(java.time.LocalDateTime.now());
            return n;
        });
    }

    @Test
    void sendSavesOnePerUserAndPublishesOnTheUserChannel() {
        UUID user = UUID.randomUUID();

        // o mesmo usuario duas vezes (ex.: responsavel de 2 alunos) recebe uma so
        service.send(List.of(user, user), NotificationType.STUDENT_BOARDED, "Ana embarcou", "Embarcou às 07:10",
                Map.of("tripId", "t1"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).saveAndFlush(saved.capture());
        assertEquals("{\"tripId\":\"t1\"}", saved.getValue().getPayload());
        assertNull(saved.getValue().getReadAt());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("notification-" + user), message.capture());
        assertTrue(message.getValue().contains("\"type\":\"notification\""));
        assertTrue(message.getValue().contains("\"tripId\":\"t1\""));
        // a data vai junto (antes ia null e o app mostrava 1970)
        assertFalse(message.getValue().contains("\"createdAt\":null"));
    }

    @Test
    void cannotMarkSomeoneElsesNotificationAsRead() {
        UUID id = UUID.randomUUID(), other = UUID.randomUUID();
        when(notificationRepository.findByIdAndUserId(id, other)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.markRead(id, other));
    }

    @Test
    void markReadSetsTheDate() {
        UUID id = UUID.randomUUID(), user = UUID.randomUUID();
        Notification n = new Notification();
        when(notificationRepository.findByIdAndUserId(id, user)).thenReturn(Optional.of(n));

        service.markRead(id, user);

        assertNotNull(n.getReadAt());
    }
}
