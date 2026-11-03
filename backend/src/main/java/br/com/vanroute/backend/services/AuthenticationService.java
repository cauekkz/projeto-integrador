package br.com.vanroute.backend.services;

import java.util.UUID;
import br.com.vanroute.backend.config.TokenProvider;
import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.UserRepository;
import br.com.vanroute.backend.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import org.springframework.stereotype.Service;


// login com limite de tentativas: 5 senhas erradas no mesmo CPF ou 30 no mesmo IP bloqueiam por 15 minutos.
// A senha e conferida antes de tudo: CPF inexistente, senha errada e conta excluida dao a mesma resposta, e o
// "verifique seu e-mail" so aparece pra quem acertou a senha (nao da pra descobrir se um CPF tem conta)
@Service
public class AuthenticationService {

    static final int MAX_FAILURES_PER_CPF = 5;
    static final int MAX_FAILURES_PER_IP = 30;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final RedisTemplate<String, String> redisTemplate;
    @Value("${spring.jwt.expiration}")
    private Long expirationTime;


    public AuthenticationService(UserRepository userRepository, AuthenticationManager authenticationManager, TokenProvider tokenProvider,
                                 RefreshTokenService refreshTokenService, RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.redisTemplate = redisTemplate;
    }


    public ResponseEntity<TokenResponseDTO> login(LoginRequestDTO loginRequestDTO, String ip) {
        String cpfKey = "login:fail:cpf:" + loginRequestDTO.getCpf();
        String ipKey = "login:fail:ip:" + ip;
        if (failures(cpfKey) >= MAX_FAILURES_PER_CPF || failures(ipKey) >= MAX_FAILURES_PER_IP) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Tente de novo em " + LOCK_TIME.toMinutes() + " minutos");
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequestDTO.getCpf(),
                    loginRequestDTO.getPasswordHash()
            ));
        } catch (AuthenticationException e) {
            fail(cpfKey);
            fail(ipKey);
            throw new InvalidCredentialsException("Credenciais inválidas.");
        }
        redisTemplate.delete(cpfKey);
        User user = (User) auth.getPrincipal();
        // senha certa, mas o e-mail ainda nao foi confirmado
        if (user.getStatus() == UserStatus.CHECK_EMAIL) {
            throw new BadRequestException("Verifique seu e-mail antes de fazer login.");
        }
        // fora do try: erro aqui (ex.: Redis fora) nao pode virar "senha errada"
        String token = tokenProvider.generateToken(auth);
        return ResponseEntity.ok(new TokenResponseDTO(token, expirationTime,
                refreshTokenService.create(user.getId()), refreshTokenService.expiresIn()));
    }

    private long failures(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    // conta o erro; o bloqueio vale 15 minutos a partir do primeiro erro da sequencia
    private void fail(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCK_TIME);
        }
    }

    // troca o refreshToken por um par novo (o usado deixa de valer); 401 se venceu, ja foi usado ou foi feito logout
    public TokenResponseDTO refresh(String refreshToken) {
        UUID userId = refreshTokenService.consume(refreshToken);
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCredentialsException("Sessão expirada. Faça login novamente."));
        return new TokenResponseDTO(tokenProvider.generateToken(user), expirationTime,
                refreshTokenService.create(userId), refreshTokenService.expiresIn());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

}
