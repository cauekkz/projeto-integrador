package br.com.vanroute.backend.config;

import br.com.vanroute.backend.dtos.chat.ChatMessageResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonConfigTest {

    // o ChatController publica esse DTO no Redis com esse ObjectMapper; se falhar, o WebSocket nao recebe nada
    @Test
    void shouldSerializeChatMessageWithDate() throws Exception {
        ChatMessageResponseDTO msg = new ChatMessageResponseDTO(UUID.randomUUID(), UUID.randomUUID(), "oi",
                null, null, LocalDateTime.of(2026, 10, 21, 8, 30), null);

        String json = new JacksonConfig().objectMapper().writeValueAsString(msg);

        assertTrue(json.contains("\"sentAt\":\"2026-10-21T08:30:00\""), json);
    }
}
