package br.com.vanroute.backend.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import br.com.vanroute.backend.services.ChatService;

    //bglh complicadinho

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler implements MessageListener {
    
    private final ChatService chatService;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    
    public ChatWebSocketHandler(ChatService chatService) {
        this.chatService = chatService;
    }
    //aqui é pra verificar se ele tem permisao pra entrar na conversa antes de tudo
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String chatId = getChatIdFromUri(session.getUri());
        
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
            return;
        }
        
        String cpf = session.getPrincipal().getName();
        try {
            chatService.validateChatAccess(UUID.fromString(chatId), cpf);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        
        sessions.computeIfAbsent(chatId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }
        //antes que eu esqueça, o primeiro tem uma config do redis la, mandaram pra chat-qualquercoisa ent manda pros back o canal e a msg que foi, buft back pega isso e erifica
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String chatId = channel.replace("chat-", "");
        String payload = new String(message.getBody());
        
        Set<WebSocketSession> chatSessions = sessions.get(chatId);
        if (chatSessions != null) {
            chatSessions.forEach(s -> {
                try {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(payload));
                    }
                } catch (IOException e) {
                    //Borussia Dortmund 1 X 2 Bayern Müchen
                }
            });
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String chatId = getChatIdFromUri(session.getUri());
        Set<WebSocketSession> chatSessions = sessions.get(chatId);
        if (chatSessions != null) {
            chatSessions.remove(session);
            if (chatSessions.isEmpty()) {
                sessions.remove(chatId);
            }
        }
    }
    
    private String getChatIdFromUri(URI uri) {
        String[] parts = uri.getPath().split("/");
        return parts[parts.length - 1]; 
}
}