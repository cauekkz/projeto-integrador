package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

// token de renovacao: o app guarda e troca por um JWT novo quando o de 15 min vence, sem pedir a senha.
// no Redis fica so o hash dele (quem le o Redis nao consegue usar) e a chave some sozinha no fim da validade.
// cada uso gera outro e o antigo deixa de valer; se um ja usado volta, e roubo: caem todos os tokens do usuario
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${spring.jwt.refresh-expiration:2592000000}")
    private long refreshExpiration;

    public RefreshTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String create(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);
        Duration ttl = Duration.ofMillis(refreshExpiration);
        redisTemplate.opsForValue().set("refresh:" + hash, userId.toString(), ttl);
        // tokens validos do usuario (pra derrubar todos de uma vez)
        redisTemplate.opsForSet().add(userKey(userId), hash);
        redisTemplate.expire(userKey(userId), ttl);
        return token;
    }

    // devolve o dono e invalida o token (uso unico).
    // token ja usado aparecendo de novo = alguem copiou: derruba todos os tokens do usuario (o ladrao e o dono saem;
    // o dono entra de novo com a senha, o ladrao nao)
    public UUID consume(String token) {
        if (token == null) {
            throw expired();
        }
        String hash = hash(token);
        String userId = redisTemplate.opsForValue().getAndDelete("refresh:" + hash);
        if (userId == null) {
            String owner = redisTemplate.opsForValue().get("refresh:used:" + hash);
            if (owner != null) {
                revokeAll(UUID.fromString(owner));
            }
            throw expired();
        }
        redisTemplate.opsForSet().remove(userKey(UUID.fromString(userId)), hash);
        redisTemplate.opsForValue().set("refresh:used:" + hash, userId, Duration.ofMillis(refreshExpiration));
        return UUID.fromString(userId);
    }

    public void revoke(String token) {
        if (token == null) {
            return;
        }
        String hash = hash(token);
        String userId = redisTemplate.opsForValue().getAndDelete("refresh:" + hash);
        if (userId != null) {
            redisTemplate.opsForSet().remove(userKey(UUID.fromString(userId)), hash);
        }
    }

    // sai de todos os aparelhos
    public void revokeAll(UUID userId) {
        Set<String> hashes = redisTemplate.opsForSet().members(userKey(userId));
        if (hashes != null) {
            hashes.forEach(h -> redisTemplate.delete("refresh:" + h));
        }
        redisTemplate.delete(userKey(userId));
    }

    public long expiresIn() {
        return refreshExpiration;
    }

    private static InvalidCredentialsException expired() {
        return new InvalidCredentialsException("Sessão expirada. Faça login novamente.");
    }

    private static String userKey(UUID userId) {
        return "refresh:user:" + userId;
    }

    private static String hash(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
