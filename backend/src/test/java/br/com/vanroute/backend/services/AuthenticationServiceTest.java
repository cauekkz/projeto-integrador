package br.com.vanroute.backend.services;

import br.com.vanroute.backend.config.TokenProvider;
import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.apache.coyote.BadRequestException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    private RolesRepository rolesRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(
                authenticationService,
                "expirationTime",
                3600L
        );
    }

    @Test
    void shouldLoginSuccessfullyWhenUserIsActive() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("12312312312", "123123");

        User user = new User();
        user.setCpf("12312312312");
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByCpf("12312312312")).thenReturn(Optional.of(user));

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");

        ResponseEntity<TokenResponseDTO> response = authenticationService.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("jwt-token", response.getBody().token());
    }

    @Test
    void shouldThrowWhenUserIsNotFound() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "senhaErrada");
        when(userRepository.findByCpf("12345678900")).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request)
        );

        assertEquals("Credenciais inválidas.", exception.getMessage());
    }

    @Test
    void shouldThrowWhenEmailIsNotVerified() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "senha123");

        User user = new User();
        user.setCpf("12345678900");
        user.setStatus(UserStatus.CHECK_EMAIL);
        when(userRepository.findByCpf("12345678900")).thenReturn(Optional.of(user));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> authenticationService.login(request)
        );

        assertEquals("Verifique seu e-mail antes de fazer login.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCredentialsAreInvalid() {
        LoginRequestDTO request = new LoginRequestDTO("12345678900", "senhaErrada");

        User user = new User();
        user.setCpf("12345678900");
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByCpf("12345678900")).thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authenticationService.login(request)
        );

        assertEquals("Credenciais inválidas.", exception.getMessage());
    }
}
