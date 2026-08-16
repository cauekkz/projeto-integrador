package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.UpdateUser;
import br.com.vanroute.backend.exceptions.UserOrDriverOrResponsibleAlreadyRegisteredException;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final EmailVerificationService emailVerificationService;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RolesRepository rolesRepository, EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolesRepository = rolesRepository;
        this.emailVerificationService = emailVerificationService;
    }

    public User createUser(UserCreateDTO userCreateDTO) {
        if (findByCpf(userCreateDTO.getCpf()).isPresent()) {
            throw new UserOrDriverOrResponsibleAlreadyRegisteredException("Usuario ja cadastrado");
        }

        RoleTypeEnum selectedRole = userCreateDTO.getRole() != null ? userCreateDTO.getRole()
                : RoleTypeEnum.ROLE_RESPONSIBLE;
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
        return userRepository.save(user);

    }

    public String sendCodeToUpdate(String cpf){
        User user = userRepository.findByCpf(cpf)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String userEmail = user.getEmail();
        String redisKey = "User:SendCodeToUpdate:" + cpf;
        emailVerificationService.generateAndSendCode(redisKey, userEmail);
        return "Code sent in " + userEmail;
    }

    public String updateUser(UpdateUser updateUser, String cpf){
        User user = userRepository.findByCpf(cpf)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String redisKey = "User:SendCodeToUpdate:" + cpf;

        emailVerificationService.verifyCode(redisKey, updateUser.getCode());
        if(updateUser.getEmail() != null){
            user.setEmail(updateUser.getEmail());
        }
        if(updateUser.getPhone() != null){
            user.setPhone(updateUser.getPhone());
        }
         userRepository.save(user);
        return "User updated successfully";
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

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }
}
