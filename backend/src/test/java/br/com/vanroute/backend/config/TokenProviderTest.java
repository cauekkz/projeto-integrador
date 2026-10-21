package br.com.vanroute.backend.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenProviderTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef";

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new TokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "key", KEY);
    }

    private String tokenWithExpiration(long expirationMillis) {
        ReflectionTestUtils.setField(tokenProvider, "expirationTime", expirationMillis);
        User principal = new User("12345678901", "x", List.of(new SimpleGrantedAuthority("ROLE_DRIVER")));
        return tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void shouldIssueTokenWithExpirationAfterIssuedAt() {
        Claims claims = tokenProvider.getClaims(tokenWithExpiration(60_000));

        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
        assertEquals("12345678901", claims.getSubject());
    }

    @Test
    void shouldRejectExpiredToken() {
        assertFalse(tokenProvider.isTokenValid(tokenWithExpiration(-1_000)));
    }

    @Test
    void subjectIsTheUserIdForAppUsers() {
        ReflectionTestUtils.setField(tokenProvider, "expirationTime", 60_000L);
        br.com.vanroute.backend.models.user.User appUser = new br.com.vanroute.backend.models.user.User();
        appUser.setId(java.util.UUID.randomUUID());
        appUser.setCpf("12345678901");

        String token = tokenProvider.generateToken(new UsernamePasswordAuthenticationToken(appUser, null, List.of()));

        assertEquals(appUser.getId().toString(), tokenProvider.getSubject(token));
    }
}
