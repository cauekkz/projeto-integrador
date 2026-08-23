package br.com.vanroute.backend.websocket;

import br.com.vanroute.backend.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatWebSocketHandlerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatWebSocketHandler chatWebSocketHandler;

    @Mock
    private WebSocketSession session;

    @Mock
    private Principal principal;

    private UUID chatId;
    private String cpf;

    @BeforeEach
    void setUp() throws Exception {
        chatId = UUID.randomUUID();
        cpf = "12345678901";
    }

    @Test
    void shouldCloseConnectionWhenUnauthenticated() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/chat/" + chatId));
        when(session.getPrincipal()).thenReturn(null);

        chatWebSocketHandler.afterConnectionEstablished(session);

        verify(session, times(1)).close(argThat(status -> status.getCode() == CloseStatus.NOT_ACCEPTABLE.getCode()));
        verify(chatService, never()).validateChatAccess(any(), any());
    }

    @Test
    void shouldCloseConnectionWhenUnauthorizedAccess() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/chat/" + chatId));
        when(session.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(cpf);
        doThrow(new IllegalArgumentException("Você não tem acesso a este chat."))
                .when(chatService).validateChatAccess(chatId, cpf);

        chatWebSocketHandler.afterConnectionEstablished(session);

        verify(session, times(1)).close(argThat(status -> status.getCode() == CloseStatus.NOT_ACCEPTABLE.getCode()));
    }

    @Test
    void shouldAcceptConnectionAndReceiveRedisMessage() throws Exception {
        when(session.getUri()).thenReturn(new URI("ws://localhost/chat/" + chatId));
        when(session.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(cpf);
        when(session.isOpen()).thenReturn(true);
        
        doNothing().when(chatService).validateChatAccess(chatId, cpf);

        // Estabiliza conexão
        chatWebSocketHandler.afterConnectionEstablished(session);

        verify(session, never()).close(any());
        
        // Simula recebimento de mensagem pelo Redis
        byte[] channel = ("chat-" + chatId.toString()).getBytes();
        byte[] body = "{\"content\":\"Test message\"}".getBytes();
        DefaultMessage message = new DefaultMessage(channel, body);
        
        chatWebSocketHandler.onMessage(message, "chat-*".getBytes());
        
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
