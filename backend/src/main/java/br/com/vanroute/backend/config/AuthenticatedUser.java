package br.com.vanroute.backend.config;

import org.springframework.security.core.Authentication;

import java.util.UUID;

// id do usuario logado direto do token, sem ir ao banco
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
