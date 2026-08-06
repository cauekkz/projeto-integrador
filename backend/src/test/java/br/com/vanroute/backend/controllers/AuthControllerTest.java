package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.services.AuthenticationService;
import br.com.vanroute.backend.services.EmailVerificationService;
import br.com.vanroute.backend.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String EMAIL = "carolina@example.com";
    private static final String REDIS_KEY = "verificationEmail:email:" + EMAIL;

    private MockMvc mockMvc;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private UserService userService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void shouldVerifyEmailAndActivateUser() throws Exception {
        User user = new User();
        user.setEmail(EMAIL);
        user.setStatus(UserStatus.CHECK_EMAIL);

        doNothing().when(emailVerificationService).verifyCode(REDIS_KEY, "123456");
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/verify-email")
                        .param("email", EMAIL)
                        .param("code", "123456"))
                .andExpect(status().isOk())
                .andExpect(content().string("E-mail verificado com sucesso!"));

        verify(emailVerificationService, times(1)).verifyCode(REDIS_KEY, "123456");
        verify(userService, times(1)).findByEmail(EMAIL);
        verify(userService, times(1)).saveUser(user);
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void shouldReturnBadRequestWhenVerificationCodeIsInvalid() throws Exception {
        doThrow(new IllegalArgumentException("Código incorreto ou expirado. Envie um novo código para verificar seu e-mail."))
                .when(emailVerificationService).verifyCode(REDIS_KEY, "000000");

        mockMvc.perform(post("/api/auth/verify-email")
                        .param("email", EMAIL)
                        .param("code", "000000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Código incorreto ou expirado. Envie um novo código para verificar seu e-mail."));

        verify(userService, never()).saveUser(any());
    }

    @Test
    void shouldReturnBadRequestWhenUserNotFoundOnVerifyEmail() throws Exception {
        doNothing().when(emailVerificationService).verifyCode(REDIS_KEY, "123456");
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/verify-email")
                        .param("email", EMAIL)
                        .param("code", "123456"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Usuário não encontrado."));

        verify(userService, never()).saveUser(any());
    }

    @Test
    void shouldResendVerificationCode() throws Exception {
        User user = new User();
        user.setEmail(EMAIL);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(emailVerificationService.generateAndSendCode(REDIS_KEY, EMAIL)).thenReturn("654321");

        mockMvc.perform(post("/api/auth/send-verification-code")
                        .param("email", EMAIL))
                .andExpect(status().isOk())
                .andExpect(content().string("Código de verificação enviado com sucesso!"));

        verify(userService, times(1)).findByEmail(EMAIL);
        verify(emailVerificationService, times(1)).generateAndSendCode(REDIS_KEY, EMAIL);
    }

    @Test
    void shouldReturnBadRequestWhenResendingCodeForUnknownEmail() throws Exception {
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/send-verification-code")
                        .param("email", EMAIL))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Usuário não encontrado."));

        verify(emailVerificationService, never()).generateAndSendCode(any(), any());
    }
}
