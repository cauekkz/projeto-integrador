package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.services.AuthenticationService;
import br.com.vanroute.backend.services.UserService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import br.com.vanroute.backend.services.EmailVerificationService;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/auth")

public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthenticationService authenticationService, UserService userService, EmailVerificationService emailVerificationService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
    }

//    @PostMapping("/register")
//    public void register(@RequestBody @Valid UserCreateDTO userCreateDTO) throws BadRequestException {
//        authenticationService.register(userCreateDTO);
//    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody @Valid LoginRequestDTO loginRequestDTO) throws Exception {
        return ResponseEntity.ok(authenticationService.login(loginRequestDTO).getBody());
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String email, @RequestParam String code) {
        try {
            emailVerificationService.verifyCode("verificationEmail:email:" + email, code);
            User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
            user.setStatus(UserStatus.ACTIVE);
            userService.saveUser(user);
            return ResponseEntity.ok("E-mail verificado com sucesso!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/send-verification-code")
    public ResponseEntity<?> sendVerificationCode(@RequestParam String email) {
        try {
            userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
            emailVerificationService.generateAndSendCode("verificationEmail:email:" + email, email);
            return ResponseEntity.ok("Código de verificação enviado com sucesso!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


}
