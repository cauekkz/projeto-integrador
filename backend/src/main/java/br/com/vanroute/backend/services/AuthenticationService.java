package br.com.vanroute.backend.services;

import br.com.vanroute.backend.config.TokenProvider;
import br.com.vanroute.backend.dtos.user.LoginRequestDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.dtos.user.token.TokenResponseDTO;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.apache.coyote.BadRequestException;
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
    @Value("${spring.jwt.expiration}")
    private Long expirationTime;


    public AuthenticationService(UserRepository userRepository, RolesRepository rolesRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.rolesRepository = rolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    public void register(UserCreateDTO userCreateDTO) throws BadRequestException {
        User user = userRepository.findByCpf(userCreateDTO.getCpf())
                .orElse(null);
        if(user != null){
            throw new BadRequestException("Usuario ja cadastrado");
        }
        // Seleciona a role informada no DTO, ou usa RESPONSIBLE por padrão caso não informado
        RoleTypeEnum selectedRole = userCreateDTO.getRole() != null ? userCreateDTO.getRole() : RoleTypeEnum.ROLE_RESPONSIBLE;
        String roleName = selectedRole.name();

        RolesEntity roles = rolesRepository.findByNome(roleName)
                .orElseGet(() -> rolesRepository.save(RolesEntity.builder()
                        .nome(roleName).build()));

        User userSaved = new User();
        userSaved.setName(userCreateDTO.getName());
        userSaved.setCpf(userCreateDTO.getCpf());
        userSaved.setRoles(Set.of(roles));
        userSaved.setStatus(UserStatus.ACTIVE);
        userSaved.setPasswordHash(passwordEncoder.encode(userCreateDTO.getPasswordHash()));
        userRepository.save(userSaved);
    }

    public ResponseEntity<TokenResponseDTO> login(LoginRequestDTO loginRequestDTO) throws Exception {
        try {
            Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginRequestDTO.getCpf(),
                    loginRequestDTO.getPasswordHash()
            ));
            String token = tokenProvider.generateToken(auth);
            return ResponseEntity.ok(new TokenResponseDTO(token, expirationTime));
        }catch (Exception e){
            throw new Exception("Credenciais inválidas.");
        }
    }

}
