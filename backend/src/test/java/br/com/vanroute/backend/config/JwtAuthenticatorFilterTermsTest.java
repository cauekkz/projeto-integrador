package br.com.vanroute.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// sem aceitar a versao atual dos termos, so da pra ver e aceitar os termos, ver a conta, sair, exportar e excluir
class JwtAuthenticatorFilterTermsTest {

    @Test
    void routesAllowedBeforeAcceptingTheNewTerms() {
        assertTrue(JwtAuthenticatorFilter.allowedWithoutTerms("GET", "/api/terms"));
        assertTrue(JwtAuthenticatorFilter.allowedWithoutTerms("POST", "/api/terms/accept"));
        assertTrue(JwtAuthenticatorFilter.allowedWithoutTerms("GET", "/api/users/me"));
        assertTrue(JwtAuthenticatorFilter.allowedWithoutTerms("GET", "/api/users/me/export"));
        assertTrue(JwtAuthenticatorFilter.allowedWithoutTerms("DELETE", "/api/users/me"));
        assertTrue(JwtAuthenticatorFilter.allowedWithoutTerms("POST", "/api/auth/logout"));
    }

    @Test
    void everythingElseWaitsForTheTerms() {
        assertFalse(JwtAuthenticatorFilter.allowedWithoutTerms("GET", "/api/contract/my-contracts"));
        assertFalse(JwtAuthenticatorFilter.allowedWithoutTerms("PUT", "/api/users/update-user"));
        assertFalse(JwtAuthenticatorFilter.allowedWithoutTerms("GET", "/api/chats"));
    }
}
