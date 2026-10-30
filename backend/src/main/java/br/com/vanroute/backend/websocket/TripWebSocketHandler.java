package br.com.vanroute.backend.websocket;

import br.com.vanroute.backend.services.TripLiveService;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// ws://.../trip/{tripId}?token=JWT
// motorista: manda {"type":"position","latitude":..,"longitude":..} e recebe a trip inteira.
// responsavel: so recebe (os filhos dele; van/trajeto/tempo quando o proximo ponto e do filho).
// cada mudanca (GPS, embarque, falta...) chega pelo Redis "trip-{id}" e vai pra todos os conectados
@Component
public class TripWebSocketHandler extends TextWebSocketHandler implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TripWebSocketHandler.class);
    private static final String ACCESS = "tripAccess";

    private final TripLiveService tripLiveService;
    private final ObjectMapper objectMapper;
    // tripId -> (id da sessao -> sessao segura pra envio de varias threads)
    private final Map<UUID, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public TripWebSocketHandler(TripLiveService tripLiveService, ObjectMapper objectMapper) {
        this.tripLiveService = tripLiveService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
            return;
        }
        UUID tripId = tripId(session.getUri());
        if (tripId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Trip invalida"));
            return;
        }
        TripLiveService.Access access;
        try {
            access = tripLiveService.access(tripId, UUID.fromString(session.getPrincipal().getName()));
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }
        session.getAttributes().put(ACCESS, access);
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);
        sessions.computeIfAbsent(tripId, k -> new ConcurrentHashMap<>()).put(session.getId(), safe);
        // assim que conecta ja recebe o estado atual (nada de ficar perguntando)
        send(tripId, safe);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID tripId = tripId(session.getUri());
        TripLiveService.Access access = (TripLiveService.Access) session.getAttributes().get(ACCESS);
        WebSocketSession safe = tripId == null ? null : sessions.getOrDefault(tripId, Map.of()).get(session.getId());
        if (access == null || safe == null) {
            return;
        }
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            if (!"position".equals(json.path("type").asText())) {
                error(safe, "Tipo de mensagem desconhecido");
                return;
            }
            if (!access.driver()) {
                error(safe, "Só o motorista envia a posição");
                return;
            }
            if (!json.path("latitude").isNumber() || !json.path("longitude").isNumber()) {
                error(safe, "latitude e longitude são obrigatórias");
                return;
            }
            tripLiveService.updatePosition(tripId, json.get("latitude").asDouble(), json.get("longitude").asDouble());
        } catch (Exception e) {
            error(safe, e.getMessage());
        }
    }

    // chega do Redis (de qualquer instancia do backend): manda a visao de cada um
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        UUID tripId = UUID.fromString(channel.substring("trip-".length()));
        sessions.getOrDefault(tripId, Map.of()).values().forEach(session -> send(tripId, session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID tripId = tripId(session.getUri());
        Map<String, WebSocketSession> tripSessions = tripId == null ? null : sessions.get(tripId);
        if (tripSessions != null) {
            tripSessions.remove(session.getId());
            if (tripSessions.isEmpty()) {
                sessions.remove(tripId);
            }
        }
    }

    private void send(UUID tripId, WebSocketSession session) {
        TripLiveService.Access access = (TripLiveService.Access) session.getAttributes().get(ACCESS);
        tripLiveService.state(tripId).ifPresent(state -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(tripLiveService.view(state, access))));
                }
            } catch (Exception e) {
                log.warn("Falha ao enviar atualizacao da trip {}", tripId, e);
            }
        });
    }

    private void error(WebSocketSession session, String text) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", "error", "message", String.valueOf(text)))));
    }

    // null se o fim da URL nao e um UUID (ex.: /trip/null): a conexao fecha com 1003 em vez de estourar 1011
    private static UUID tripId(URI uri) {
        String[] parts = uri.getPath().split("/");
        try {
            return UUID.fromString(parts[parts.length - 1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
