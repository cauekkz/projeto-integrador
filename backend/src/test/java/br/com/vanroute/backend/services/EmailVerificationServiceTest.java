package br.com.vanroute.backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String REDIS_KEY = "verificationEmail:email:user@example.com";
    private static final String EMAIL = "user@example.com";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldGenerateStoreAndSendVerificationCode() {
        String code = emailVerificationService.generateAndSendCode(REDIS_KEY, EMAIL);

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations, times(1)).set(eq(REDIS_KEY), eq(code), ttlCaptor.capture());
        assertEquals(Duration.ofMinutes(15), ttlCaptor.getValue());

        verify(emailService, times(1)).sendEmail(
                eq(EMAIL),
                eq("Código de verificação - VanRoute"),
                eq("Seu código de verificação é: " + code)
        );
    }

    @Test
    void shouldVerifyCodeAndRemoveFromRedisWhenCorrect() {
        when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

        assertDoesNotThrow(() -> emailVerificationService.verifyCode(REDIS_KEY, "123456"));

        verify(valueOperations, times(1)).get(REDIS_KEY);
        verify(redisTemplate, times(1)).delete(REDIS_KEY);
    }

    @Test
    void shouldThrowWhenCodeIsWrong() {
        when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> emailVerificationService.verifyCode(REDIS_KEY, "000000")
        );

        assertEquals(
                "Código incorreto ou expirado. Envie um novo código para verificar seu e-mail.",
                exception.getMessage()
        );
        verify(redisTemplate, never()).delete(eq(REDIS_KEY));
    }

    @Test
    void shouldThrowWhenCodeIsMissingOrExpired() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> emailVerificationService.verifyCode(REDIS_KEY, "123456")
        );

        assertEquals(
                "Código incorreto ou expirado. Envie um novo código para verificar seu e-mail.",
                exception.getMessage()
        );
        verify(redisTemplate, never()).delete(eq(REDIS_KEY));
    }
}
