package br.com.vanroute.backend.websocket;

import br.com.vanroute.backend.services.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// ws://.../notifications?token=JWT: cada usuario recebe as notificacoes dele (o token diz quem e).
// o app abre ao entrar e deixa aberto; quem estava fora ve depois em GET /api/notifications
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    // userId -> (id da sessao -> sessao); o mesmo usuario pode estar em mais de um aparelho
    private final Map<UUID, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
            return;
        }
        UUID userId = UUID.fromString(session.getPrincipal().getName());
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(session.getId(), safe);
        // ao conectar: quantas nao lidas (pro badge)
        safe.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", "unread-count", "count", notificationService.unreadCount(userId)))));
    }

    // chega do Redis "notification-{userId}" (de qualquer instancia do backend): repassa pros aparelhos dele
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        UUID userId = UUID.fromString(channel.substring("notification-".length()));
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        sessions.getOrDefault(userId, Map.of()).values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(body));
                }
            } catch (Exception e) {
                log.warn("Falha ao enviar notificação ao usuário {}", userId, e);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getPrincipal() == null) {
            return;
        }
        UUID userId = UUID.fromString(session.getPrincipal().getName());
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session.getId());
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }
}
