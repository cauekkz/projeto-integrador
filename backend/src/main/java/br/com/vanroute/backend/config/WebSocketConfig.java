package br.com.vanroute.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import br.com.vanroute.backend.websocket.ChatWebSocketHandler;
import br.com.vanroute.backend.websocket.WebSocketAuthHandshakeHandler;
import br.com.vanroute.backend.websocket.WebSocketAuthHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/chat/{chatId}")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor())
                .setHandshakeHandler(new WebSocketAuthHandshakeHandler())
                .setAllowedOrigins("*");
    }
}
