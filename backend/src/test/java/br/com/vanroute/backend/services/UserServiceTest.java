package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RolesRepository rolesRepository;

    @InjectMocks
    private UserService userService;

    private UserCreateDTO defaultUserCreateDTO;

    @BeforeEach
    void setUp() {
        defaultUserCreateDTO = new UserCreateDTO();
        defaultUserCreateDTO.setName("Mbappé");
        defaultUserCreateDTO.setEmail("mbappe@teste.com");
        defaultUserCreateDTO.setPhone("11999999999");
        defaultUserCreateDTO.setCpf("12345678901");
        defaultUserCreateDTO.setPasswordHash("championsLeague");
        defaultUserCreateDTO.setRole(RoleTypeEnum.ROLE_RESPONSIBLE);
    }

    @Test
    void shouldCreateUserSuccessfully() {
        when(userRepository.findByCpf(anyString())).thenReturn(Optional.empty());
        when(rolesRepository.findByNome(anyString())).thenReturn(Optional.of(RolesEntity.builder().nome("ROLE_RESPONSIBLE").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        
        User savedUser = new User();
        savedUser.setCpf("12345678901");
        savedUser.setName("Mbappé");
        
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.createUser(defaultUserCreateDTO);

        assertNotNull(result);
        assertEquals("12345678901", result.getCpf());
        assertEquals("Mbappé", result.getName());
        
        verify(userRepository, times(1)).findByCpf(anyString());
        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, times(1)).encode("championsLeague");
    }

    @Test
    void shouldThrowExceptionWhenCpfAlreadyExists() {
        when(userRepository.findByCpf(anyString())).thenReturn(Optional.of(new User()));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> userService.createUser(defaultUserCreateDTO));

        assertEquals("Usuario ja cadastrado", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
