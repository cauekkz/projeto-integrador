package br.com.vanroute.backend.services;

import br.com.vanroute.backend.config.TokenProvider;
import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.UserRepository;
import br.com.vanroute.backend.exceptions.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @InjectMocks
    private AuthenticationService authenticationService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String IP = "10.0.0.1";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(
                authenticationService,
                "expirationTime",
                3600L
        );
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private static Authentication authenticated(User user) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        return authentication;
    }

    @Test
    void shouldLoginSuccessfullyWhenUserIsActive() {
        LoginRequestDTO request = new LoginRequestDTO("12312312312", "123123");

        User user = new User();
        user.setCpf("12312312312");
        user.setStatus(UserStatus.ACTIVE);
        Authentication authentication = authenticated(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");
        when(refreshTokenService.create(any())).thenReturn("refresh-token");

        ResponseEntity<TokenResponseDTO> response = authenticationService.login(request, IP);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("jwt-token", response.getBody().token());
        assertEquals("refresh-token", response.getBody().refreshToken());
        // acertou: zera as tentativas do CPF
        verify(redisTemplate).delete("login:fail:cpf:12312312312");
    }

    @Test
    void refreshGivesANewPairForAnActiveUser() {
        User user = new User();
        java.util.UUID id = java.util.UUID.randomUUID();
        user.setId(id);
        user.setStatus(UserStatus.ACTIVE);
        when(refreshTokenService.consume("old")).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(user)).thenReturn("new-jwt");
        when(refreshTokenService.create(id)).thenReturn("new-refresh");

        TokenResponseDTO tokens = authenticationService.refresh("old");

        assertEquals("new-jwt", tokens.token());
        assertEquals("new-refresh", tokens.refreshToken());
    }

    @Test
    void refreshIsRefusedForABlockedUser() {
        User user = new User();
        java.util.UUID id = java.util.UUID.randomUUID();
        user.setStatus(UserStatus.CHECK_EMAIL);
        when(refreshTokenService.consume("old")).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class, () -> authenticationService.refresh("old"));
    }

    @Test
    void unknownCpfAndWrongPasswordGiveTheSameAnswerAndCountTheFailure() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "senhaErrada");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        when(valueOperations.increment(any())).thenReturn(1L);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request, IP)
        );

        assertEquals("Credenciais inválidas.", exception.getMessage());
        verify(valueOperations).increment("login:fail:cpf:12345678900");
        verify(valueOperations).increment("login:fail:ip:" + IP);
        verify(redisTemplate).expire("login:fail:cpf:12345678900", AuthenticationService.LOCK_TIME);
    }

    @Test
    void unverifiedEmailIsOnlyToldAfterTheRightPassword() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "senha123");

        User user = new User();
        user.setCpf("12345678900");
        user.setStatus(UserStatus.CHECK_EMAIL);
        Authentication authentication = authenticated(user);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authenticationService.login(request, IP)
        );

        assertEquals("Verifique seu e-mail antes de fazer login.", exception.getMessage());
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void lockedCpfIsRefusedWithoutCheckingThePassword() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "qualquer");
        when(valueOperations.get("login:fail:cpf:12345678900")).thenReturn(String.valueOf(AuthenticationService.MAX_FAILURES_PER_CPF));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authenticationService.login(request, IP));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void lockedIpIsRefused() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "qualquer");
        when(valueOperations.get("login:fail:cpf:12345678900")).thenReturn(null);
        when(valueOperations.get("login:fail:ip:" + IP)).thenReturn(String.valueOf(AuthenticationService.MAX_FAILURES_PER_IP));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authenticationService.login(request, IP));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        verifyNoInteractions(authenticationManager);
    }
}
