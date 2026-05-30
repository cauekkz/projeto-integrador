package br.com.vanroute.backend.services;

import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.UserStatus;
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

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public User createUser(UserCreateDTO userDto) {
        User user = new User();
        user.setName(userDto.getName());
        user.setCpf(userDto.getCpf());
        user.setPasswordHash(passwordEncoder.encode(userDto.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }


    public void deleteById(UUID id) {
        userRepository.deleteById(id);
    }

    public Optional<User> findByCpf(String cpf) {
        return userRepository.findByCpf(cpf);
    }
}
