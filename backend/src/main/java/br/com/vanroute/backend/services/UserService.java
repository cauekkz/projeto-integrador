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

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolesRepository rolesRepository;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RolesRepository rolesRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolesRepository = rolesRepository;
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
        user.setStatus(UserStatus.ACTIVE);
         return userRepository.save(user);

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
}
