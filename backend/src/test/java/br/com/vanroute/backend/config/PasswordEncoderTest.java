package br.com.vanroute.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new SecurityConfig(null).passwordEncoder();

    @Test
    void hashesWithArgon2idAndTheOwaspParameters() {
        String hash = encoder.encode("Senha@123");

        assertTrue(hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"), hash);
        assertTrue(encoder.matches("Senha@123", hash));
        assertFalse(encoder.matches("Senha@124", hash));
    }

    @Test
    void samePasswordGetsADifferentHashEachTime() {
        assertNotEquals(encoder.encode("Senha@123"), encoder.encode("Senha@123"));
    }
}
