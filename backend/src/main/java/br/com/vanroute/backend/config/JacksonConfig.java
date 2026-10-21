package br.com.vanroute.backend.config;
//chat falou que spring fazia DI disso automatico mas nao fez ai criei
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // sem o JavaTimeModule o LocalDateTime do ChatMessageResponseDTO quebrava a publicacao no Redis
        // e nenhuma mensagem chegava pelo WebSocket
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}