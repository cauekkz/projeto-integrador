package br.com.vanroute.backend.services;

import java.util.UUID;
import br.com.vanroute.backend.config.TokenProvider;
import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.exceptions.InvalidCredentialsException;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import br.com.vanroute.backend.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RolesRepository rolesRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    @Value("${spring.jwt.expiration}")
    private Long expirationTime;


    public AuthenticationService(UserRepository userRepository, RolesRepository rolesRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, TokenProvider tokenProvider,
                                 RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.rolesRepository = rolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

//    public void register(UserCreateDTO userCreateDTO) throws BadRequestException {
//        User user = userRepository.findByCpf(userCreateDTO.getCpf())
//                .orElse(null);
//        if(user != null){
//            throw new BadRequestException("Usuario ja cadastrado");
//        }
//        RoleTypeEnum selectedRole = userCreateDTO.getRole() != null ? userCreateDTO.getRole() : RoleTypeEnum.ROLE_RESPONSIBLE;
//        String roleName = selectedRole.name();
//
//        RolesEntity roles = rolesRepository.findByNome(roleName)
//                .orElseGet(() -> rolesRepository.save(RolesEntity.builder()
//                        .nome(roleName).build()));
//
//        User newUser = new User();
//        newUser.setName(userCreateDTO.getName());
//        newUser.setCpf(userCreateDTO.getCpf());
//        newUser.setRoles(Set.of(roles));
//        newUser.setStatus(UserStatus.ACTIVE);
//        newUser.setPasswordHash(passwordEncoder.encode(userCreateDTO.getPasswordHash()));
//        newUser.setEmail(userCreateDTO.getEmail());
//        newUser.setPhone(userCreateDTO.getPhone());
//
//        userRepository.save(newUser);
//    }

    public ResponseEntity<TokenResponseDTO> login(LoginRequestDTO loginRequestDTO) throws Exception {
        User user = userRepository.findByCpf(loginRequestDTO.getCpf())
            .orElseThrow(() -> new InvalidCredentialsException("Credenciais inválidas."));
            
        if (user.getStatus() == UserStatus.CHECK_EMAIL) {
            throw new BadRequestException("Verifique seu e-mail antes de fazer login.");
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequestDTO.getCpf(),
                    loginRequestDTO.getPasswordHash()
            ));
        }catch (Exception e){
            throw new InvalidCredentialsException("Credenciais inválidas.");
        }
        // fora do try: erro aqui (ex.: Redis fora) nao pode virar "senha errada"
        String token = tokenProvider.generateToken(auth);
        return ResponseEntity.ok(new TokenResponseDTO(token, expirationTime,
                refreshTokenService.create(user.getId()), refreshTokenService.expiresIn()));
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
