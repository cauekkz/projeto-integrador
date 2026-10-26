package br.com.vanroute.backend.config;

import br.com.vanroute.backend.websocket.TripWebSocketHandler;
import br.com.vanroute.backend.websocket.NotificationWebSocketHandler;
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
    private final TripWebSocketHandler tripWebSocketHandler;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler, TripWebSocketHandler tripWebSocketHandler,
                           NotificationWebSocketHandler notificationWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.tripWebSocketHandler = tripWebSocketHandler;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/chat/{chatId}")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor())
                .setHandshakeHandler(new WebSocketAuthHandshakeHandler())
                .setAllowedOrigins("*");
        // GPS do motorista e acompanhamento dos pais em tempo real
        registry.addHandler(tripWebSocketHandler, "/trip/{tripId}")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor())
                .setHandshakeHandler(new WebSocketAuthHandshakeHandler())
                .setAllowedOrigins("*");
        // notificacoes do usuario do token
        registry.addHandler(notificationWebSocketHandler, "/notifications")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor())
                .setHandshakeHandler(new WebSocketAuthHandshakeHandler())
                .setAllowedOrigins("*");
    }
}
