package br.com.vanroute.backend.services;

import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.dtos.user.UserMeResponseDTO;
import org.springframework.transaction.annotation.Transactional;

import br.com.vanroute.backend.exceptions.BadRequestException;

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

        private static final String REDIS_KEY = "User:SendCodeUser";


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolesRepository rolesRepository;
    private final EmailVerificationService emailVerificationService;
    private final StorageService storageService;
    private final DriverRepository driverRepository;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RolesRepository rolesRepository, EmailVerificationService emailVerificationService, StorageService storageService, DriverRepository driverRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolesRepository = rolesRepository;
        this.emailVerificationService = emailVerificationService;
        this.storageService = storageService;
        this.driverRepository = driverRepository;
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

    // um codigo por usuario (antes a chave era unica e um usuario sobrescrevia/usava o codigo do outro)
    private String codeKey(UUID userId) {
        return REDIS_KEY + ":" + userId;
    }

    public void sendCodeToUser(UUID userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String userEmail = user.getEmail();
        emailVerificationService.generateAndSendCode(codeKey(user.getId()), userEmail);
    }

    public String updateUser(UpdateUser updateUser, UUID userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        emailVerificationService.verifyCode(codeKey(user.getId()), updateUser.getCode());
        if(updateUser.getEmail() != null){
            user.setEmail(updateUser.getEmail());
        }
        if(updateUser.getPhone() != null){
            user.setPhone(updateUser.getPhone());
        }
         userRepository.save(user);
        return "User updated successfully";
    }

    public StorageService.PresignedUrlResponse photoUploadUrl(UUID userId, String fileName, String contentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return storageService.generateProfilePhotoUploadUrl(user.getId(), fileName, contentType);
    }

    // so aceita arquivo que o proprio usuario subiu na pasta dele; a foto antiga e apagada do bucket
    @Transactional
    public User updatePhoto(UUID userId, String fileKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (fileKey == null || !fileKey.startsWith(StorageService.PROFILE_PREFIX + user.getId() + "/")) {
            throw new BadRequestException("Arquivo inválido para foto de perfil");
        }
        if (!storageService.exists(fileKey)) {
            throw new BadRequestException("Arquivo não encontrado. Faça o upload antes");
        }
        String oldPhoto = user.getPhotoKey();
        user.setPhotoKey(fileKey);
        userRepository.save(user);
        if (oldPhoto != null && !oldPhoto.equals(fileKey)) {
            storageService.delete(oldPhoto);
        }
        return user;
    }

    public UserMeResponseDTO me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String linkCode = driverRepository.findById(user.getId()).map(Driver::getLinkCode).orElse(null);
        return new UserMeResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getCpf(),
                user.getRoles().stream().map(RolesEntity::getAuthority).toList(),
                user.getPhotoKey(),
                linkCode
        );
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public void deleteById(UUID id, String code) {
        emailVerificationService.verifyCode(codeKey(id), code);
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
