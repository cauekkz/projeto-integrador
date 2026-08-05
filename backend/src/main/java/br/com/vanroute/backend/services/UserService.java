package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.UserOrDriverOrResponsibleAlreadyRegisteredException;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;

import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolesRepository rolesRepository;
    private final EmailService emailService;
    private final Map<String, String> emailCodeCache = new ConcurrentHashMap<>();

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RolesRepository rolesRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolesRepository = rolesRepository;
        this.emailService = emailService;
    }

    public User createUser(UserCreateDTO userCreateDTO) {
        if (findByCpf(userCreateDTO.getCpf()).isPresent()) {
            throw new UserOrDriverOrResponsibleAlreadyRegisteredException("Usuario ja cadastrado");
        }

        RoleTypeEnum selectedRole = userCreateDTO.getRole() != null ? userCreateDTO.getRole() : RoleTypeEnum.ROLE_RESPONSIBLE;
        String roleName = selectedRole.name();

        RolesEntity roles = rolesRepository.findByNome(roleName)
                .orElseGet(() -> rolesRepository.save(RolesEntity.builder().nome(roleName).build()));

        User user = new User();
        user.setName(userCreateDTO.getName());
        user.setEmail(userCreateDTO.getEmail());
        user.setPhone(userCreateDTO.getPhone());          
        user.setCpf(userCreateDTO.getCpf());
        user.setPasswordHash(passwordEncoder.encode(userCreateDTO.getPasswordHash()));
        user.setRoles(java.util.Set.of(roles));
        user.setStatus(UserStatus.CHECK_EMAIL);
        User savedUser = userRepository.save(user);
        
        generateAndCacheCode(savedUser.getEmail());
        
        return savedUser;

    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public void deleteById(UUID id) {
        userRepository.deleteById(id);
    }

    public Optional<User> findByCpf(String cpf) {
        return userRepository.findByCpf(cpf);
    }
    
    private void generateAndCacheCode(String email) {
        String code = String.format("%06d", new Random().nextInt(999999));
        emailCodeCache.put(email, code);
        //System.out.println("E-mail enviado para: " + email + " com o código: " + code);
        this.emailService.sendEmail(email,"Código de verificação - VanRoute", "Seu código de verificação é: " + code);

    }
    //faze um vai se fuder bglh pra exclui do bd quando o tempo expirar, e tbm ve o tempo de duração desse cache, 
    public void verifyEmailCode(String email, String code) {
        String cachedCode = emailCodeCache.get(email);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new IllegalArgumentException("Código incorreto ou expirado.");
        }
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
                
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        emailCodeCache.remove(email);
    }
}
