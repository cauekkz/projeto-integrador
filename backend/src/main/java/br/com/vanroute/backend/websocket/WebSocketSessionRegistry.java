package br.com.vanroute.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// todas as sessoes WebSocket abertas nesta instancia (chat, notificacoes e viagem), por usuario. O token so e
// conferido no handshake: quando a conta e excluida, o backend publica o id no canal "ws-revoke" do Redis e
// cada instancia fecha as sessoes dessa pessoa
@Component
public class WebSocketSessionRegistry implements MessageListener {

    public static final String REVOKE_CHANNEL = "ws-revoke";
    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final Map<UUID, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        UUID userId = userOf(session);
        if (userId != null) {
            byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    public void unregister(WebSocketSession session) {
        UUID userId = userOf(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = byUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                byUser.remove(userId, sessions);
            }
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        UUID userId;
        try {
            userId = UUID.fromString(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return;
        }
        Set<WebSocketSession> sessions = byUser.remove(userId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Conta excluída"));
            } catch (Exception e) {
                log.debug("Falha ao fechar sessao WebSocket", e);
            }
        }
    }

    private static UUID userOf(WebSocketSession session) {
        try {
            return session.getPrincipal() == null ? null : UUID.fromString(session.getPrincipal().getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
