package br.com.vanroute.backend.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
//chat lixo
public class WebSocketAuthHandshakeHandler extends DefaultHandshakeHandler {

    static final String AUTHENTICATION_ATTRIBUTE = "AUTHENTICATION";

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object authentication = attributes.get(AUTHENTICATION_ATTRIBUTE);
        if (authentication instanceof Authentication auth) {
            return auth;
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}
