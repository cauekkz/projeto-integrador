package br.com.vanroute.backend.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
public class EmailVerificationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;

    @Autowired
    public EmailVerificationService(RedisTemplate<String, String> redisTemplate, EmailService emailService) {
            this.redisTemplate = redisTemplate;
            this.emailService = emailService;
    }

   
    public String generateAndSendCode(String redisKey, String email) {
        String code = String.format("%06d", new Random().nextInt(999999));
        
        redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(15));
        
        emailService.sendEmail(email, "Código de verificação - VanRoute",
                "Seu código de verificação é: " + code);
                
        return code;
    }

  
    public void verifyCode(String redisKey, String code) {
        String storedCode = redisTemplate.opsForValue().get(redisKey);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new IllegalArgumentException("Código incorreto ou expirado. Envie um novo código para verificar seu e-mail.");
        }
        
        redisTemplate.delete(redisKey);
    }
    
}
