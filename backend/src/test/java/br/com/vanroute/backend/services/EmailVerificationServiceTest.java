package br.com.vanroute.backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        when(valueOperations.setIfAbsent(eq(REDIS_KEY + ":cooldown"), eq("1"), any(Duration.class))).thenReturn(true);
    }

    private static <T> T any(Class<T> type) {
        return org.mockito.ArgumentMatchers.any(type);
    }

    @Test
    void shouldGenerateStoreAndSendVerificationCode() {
        String code = emailVerificationService.generateAndSendCode(REDIS_KEY, EMAIL);

        assertTrue(code.matches("\\d{6}"));
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(REDIS_KEY), eq(code), ttlCaptor.capture());
        assertEquals(Duration.ofMinutes(15), ttlCaptor.getValue());
        // o e-mail fica ao lado, pra mandar um codigo novo se errarem 5 vezes
        verify(valueOperations).set(eq(REDIS_KEY + ":email"), eq(EMAIL), any(Duration.class));
        verify(emailService).sendEmail(eq(EMAIL), eq("Código de verificação - VanRoute"), contains(code));
    }

    @Test
    void askingAgainBeforeOneMinuteIsTooManyRequests() {
        when(valueOperations.setIfAbsent(eq(REDIS_KEY + ":cooldown"), eq("1"), any(Duration.class))).thenReturn(false);
        when(redisTemplate.getExpire(REDIS_KEY + ":cooldown")).thenReturn(42L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> emailVerificationService.generateAndSendCode(REDIS_KEY, EMAIL));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("42 segundos"));
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void signupAlwaysSendsAndStartsTheOneMinuteLock() {
        // trava de um cadastro anterior com o mesmo e-mail (ex.: conta excluida e criada de novo) nao bloqueia
        when(valueOperations.setIfAbsent(eq(REDIS_KEY + ":cooldown"), eq("1"), any(Duration.class))).thenReturn(false);

        String code = emailVerificationService.sendCodeAfterSignup(REDIS_KEY, EMAIL);

        verify(valueOperations).set(eq(REDIS_KEY + ":cooldown"), eq("1"), eq(Duration.ofMinutes(1)));
        verify(emailService).sendEmail(eq(EMAIL), anyString(), contains(code));
    }

    @Test
    void shouldVerifyCodeAndRemoveFromRedisWhenCorrect() {
        when(valueOperations.get(REDIS_KEY)).thenReturn("123456");

        assertDoesNotThrow(() -> emailVerificationService.verifyCode(REDIS_KEY, "123456"));

        verify(redisTemplate).delete(List.of(REDIS_KEY, REDIS_KEY + ":attempts", REDIS_KEY + ":email"));
    }

    @Test
    void wrongCodeSaysHowManyAttemptsAreLeft() {
        when(valueOperations.get(REDIS_KEY)).thenReturn("123456");
        when(valueOperations.increment(REDIS_KEY + ":attempts")).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> emailVerificationService.verifyCode(REDIS_KEY, "000000"));

        assertTrue(ex.getMessage().contains("Restam 4 tentativas"));
        verify(redisTemplate, never()).delete(REDIS_KEY);
    }

    @Test
    void fifthWrongAttemptSendsANewCode() {
        when(valueOperations.get(REDIS_KEY)).thenReturn("123456");
        when(valueOperations.increment(REDIS_KEY + ":attempts")).thenReturn(5L);
        when(valueOperations.get(REDIS_KEY + ":email")).thenReturn(EMAIL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> emailVerificationService.verifyCode(REDIS_KEY, "000000"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("novo código"));
        // codigo novo gravado (substitui o velho) e enviado, sem passar pela trava de 1 minuto
        verify(valueOperations).set(eq(REDIS_KEY), argThat(code -> code.matches("\\d{6}")), any(Duration.class));
        verify(emailService).sendEmail(eq(EMAIL), anyString(), contains("trocado"));
        verify(valueOperations, never()).setIfAbsent(eq(REDIS_KEY + ":cooldown"), anyString(), any(Duration.class));
    }

    @Test
    void shouldThrowWhenCodeIsMissingOrExpired() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> emailVerificationService.verifyCode(REDIS_KEY, "123456"));

        assertTrue(ex.getMessage().contains("expirado"));
        verify(valueOperations, never()).increment(anyString());
    }
}
