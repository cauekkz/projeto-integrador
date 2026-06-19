package br.com.vanroute.backend.services;

import br.com.vanroute.backend.config.TokenProvider;
import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
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
    @Order(1)
    void shouldLoginSuccessfully() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("12312312312", "123123");

        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token");

        ResponseEntity<TokenResponseDTO> response = authenticationService.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "jwt-token",
                response.getBody().token()
        );

    }

    @Test
    @Order(2)
    void shouldThrowExceptionWhenCredentialsAreInvalid(){
        LoginRequestDTO request =
                new LoginRequestDTO("12345678900", "senhaErrada");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        Exception exception = assertThrows(
                Exception.class,
                () -> authenticationService.login(request)
        );

        assertEquals(
                "Credenciais inválidas.",
                exception.getMessage()
        );
    }
}