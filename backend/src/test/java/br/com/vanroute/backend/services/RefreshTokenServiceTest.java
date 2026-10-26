package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> values;
    @Mock private SetOperations<String, String> sets;

    private final Map<String, String> redis = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(redisTemplate);
        ReflectionTestUtils.setField(service, "refreshExpiration", 2_592_000_000L);
        when(redisTemplate.opsForValue()).thenReturn(values);
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1))).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(inv -> redis.remove((String) inv.getArgument(0)));
        when(values.get(anyString())).thenAnswer(inv -> redis.get((String) inv.getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> redis.remove((String) inv.getArgument(0)) != null
                | redisSets.remove((String) inv.getArgument(0)) != null);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(sets.add(anyString(), any(String[].class))).thenAnswer(inv -> {
            redisSets.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        });
        when(sets.remove(anyString(), any())).thenAnswer(inv -> {
            redisSets.getOrDefault((String) inv.getArgument(0), new HashSet<>()).remove(inv.getArgument(1));
            return 1L;
        });
        when(sets.members(anyString())).thenAnswer(inv -> redisSets.get((String) inv.getArgument(0)));
    }

    @Test
    void tokenIsSingleUse() {
        UUID user = UUID.randomUUID();
        String token = service.create(user);

        assertEquals(user, service.consume(token));
        // usado de novo (ex.: roubado depois de usado): nao vale mais
        assertThrows(InvalidCredentialsException.class, () -> service.consume(token));
    }

    @Test
    void reusingAnOldTokenRevokesAllTokensOfTheUser() {
        UUID user = UUID.randomUUID();
        String stolen = service.create(user);
        String otherDevice = service.create(user);
        // o ladrao usa primeiro e ganha um token novo
        service.consume(stolen);
        String thiefNew = service.create(user);

        // o dono tenta usar o mesmo token: ja foi usado = roubo
        assertThrows(InvalidCredentialsException.class, () -> service.consume(stolen));

        // todo mundo cai, inclusive o token novo do ladrao
        assertThrows(InvalidCredentialsException.class, () -> service.consume(thiefNew));
        assertThrows(InvalidCredentialsException.class, () -> service.consume(otherDevice));
    }

    @Test
    void redisKeepsOnlyTheHashWithTheExpiration() {
        String token = service.create(UUID.randomUUID());

        assertEquals(1, redis.size());
        String key = redis.keySet().iterator().next();
        assertTrue(key.startsWith("refresh:"));
        assertFalse(key.contains(token), "o token em si nao fica no Redis");
        verify(values).set(eq(key), anyString(), eq(Duration.ofDays(30)));
    }

    @Test
    void logoutInvalidatesTheToken() {
        String token = service.create(UUID.randomUUID());
        service.revoke(token);

        assertThrows(InvalidCredentialsException.class, () -> service.consume(token));
    }
}
