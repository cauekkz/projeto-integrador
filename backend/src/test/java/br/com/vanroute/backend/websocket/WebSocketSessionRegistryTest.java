package br.com.vanroute.backend.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// conta excluida: o id chega pelo canal ws-revoke e as sessoes dessa pessoa fecham (as dos outros nao)
class WebSocketSessionRegistryTest {

    private static WebSocketSession sessionOf(UUID userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(userId.toString());
        when(session.getPrincipal()).thenReturn(principal);
        return session;
    }

    @Test
    void revokeClosesOnlyTheDeletedUsersSessions() throws Exception {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        UUID deleted = UUID.randomUUID();
        WebSocketSession chat = sessionOf(deleted);
        WebSocketSession trip = sessionOf(deleted);
        WebSocketSession other = sessionOf(UUID.randomUUID());
        registry.register(chat);
        registry.register(trip);
        registry.register(other);

        registry.onMessage(new DefaultMessage("ws-revoke".getBytes(StandardCharsets.UTF_8),
                deleted.toString().getBytes(StandardCharsets.UTF_8)), null);

        verify(chat).close(any(CloseStatus.class));
        verify(trip).close(any(CloseStatus.class));
        verify(other, never()).close(any(CloseStatus.class));
    }
}
