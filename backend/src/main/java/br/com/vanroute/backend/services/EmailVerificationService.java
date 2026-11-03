package br.com.vanroute.backend.services;

import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// codigo de 6 digitos por e-mail (confirmar cadastro, esqueci a senha, trocar e-mail/telefone).
// No Redis: a chave guarda so o codigo; ao lado ficam o e-mail, as tentativas erradas e a trava de reenvio
@Service
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(15);
    // pedir outro codigo antes disso da 429 (evita usar o app pra mandar e-mail em massa)
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    // na 5a tentativa errada o codigo e trocado e o novo vai por e-mail
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;

    public EmailVerificationService(RedisTemplate<String, String> redisTemplate, EmailService emailService) {
        this.redisTemplate = redisTemplate;
        this.emailService = emailService;
    }

    public String generateAndSendCode(String redisKey, String email) {
        Boolean free = redisTemplate.opsForValue().setIfAbsent(redisKey + ":cooldown", "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(free)) {
            Long seconds = redisTemplate.getExpire(redisKey + ":cooldown");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Aguarde " + (seconds == null || seconds < 1 ? 60 : seconds) + " segundos para pedir outro código");
        }
        return sendNewCode(redisKey, email, "Seu código de verificação é: ");
    }

    // cadastro: sempre manda (a pessoa acabou de criar a conta) e comeca a contar o minuto do reenvio
    public String sendCodeAfterSignup(String redisKey, String email) {
        redisTemplate.opsForValue().set(redisKey + ":cooldown", "1", RESEND_COOLDOWN);
        return sendNewCode(redisKey, email, "Seu código de verificação é: ");
    }

    public void verifyCode(String redisKey, String code) {
        String storedCode = redisTemplate.opsForValue().get(redisKey);
        if (storedCode == null) {
            throw new IllegalArgumentException("Código expirado. Peça um novo código.");
        }
        if (storedCode.equals(code)) {
            redisTemplate.delete(java.util.List.of(redisKey, redisKey + ":attempts", redisKey + ":email"));
            return;
        }
        // INCR e atomico: duas tentativas ao mesmo tempo nao passam do limite
        Long attempts = redisTemplate.opsForValue().increment(redisKey + ":attempts");
        redisTemplate.expire(redisKey + ":attempts", CODE_TTL);
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            String email = redisTemplate.opsForValue().get(redisKey + ":email");
            if (email == null) {
                redisTemplate.delete(java.util.List.of(redisKey, redisKey + ":attempts"));
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas erradas. Peça um novo código.");
            }
            sendNewCode(redisKey, email, "Houve muitas tentativas erradas com o código anterior, então ele foi trocado. Seu novo código é: ");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas erradas. Enviamos um novo código para o seu e-mail.");
        }
        long left = MAX_ATTEMPTS - (attempts == null ? 0 : attempts);
        throw new IllegalArgumentException("Código incorreto. " + (left == 1 ? "Resta 1 tentativa." : "Restam " + left + " tentativas."));
    }

    private String sendNewCode(String redisKey, String email, String text) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(redisKey, code, CODE_TTL);
        redisTemplate.opsForValue().set(redisKey + ":email", email, CODE_TTL);
        redisTemplate.delete(redisKey + ":attempts");
        emailService.sendEmail(email, "Código de verificação - VanRoute", text + code);
        return code;
    }
}
