package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.UpdateUser;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.exceptions.UserOrDriverOrResponsibleAlreadyRegisteredException;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.models.user.enums.UserStatus;
import br.com.vanroute.backend.repositories.RolesRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RolesRepository rolesRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private UserService userService;

    private UserCreateDTO defaultUserCreateDTO;
    private RolesEntity responsibleRole;
    private User defaultUser;

    @BeforeEach
    void setUp() {
        defaultUserCreateDTO = new UserCreateDTO();

        defaultUserCreateDTO.setName("Mbappé");
        defaultUserCreateDTO.setEmail("mbappe@teste.com");
        defaultUserCreateDTO.setPhone("11999999999");
        defaultUserCreateDTO.setCpf("12345678901");
        defaultUserCreateDTO.setPasswordHash("championsLeague");
        defaultUserCreateDTO.setRole(RoleTypeEnum.ROLE_RESPONSIBLE);

        responsibleRole = RolesEntity.builder()
                .nome("ROLE_RESPONSIBLE")
                .build();

        defaultUser = new User();
        defaultUser.setName("Mbappé");
        defaultUser.setEmail("mbappe@teste.com");
        defaultUser.setPhone("11999999999");
        defaultUser.setCpf("12345678901");
        defaultUser.setStatus(UserStatus.CHECK_EMAIL);
    }

    // =========================================================
    // CREATE USER
    // =========================================================

    @Test
    void shouldCreateUserSuccessfully() {

        when(userRepository.findByCpf(anyString()))
                .thenReturn(Optional.empty());

        when(rolesRepository.findByNome("ROLE_RESPONSIBLE"))
                .thenReturn(Optional.of(responsibleRole));

        when(passwordEncoder.encode("championsLeague"))
                .thenReturn("hashed_password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(defaultUserCreateDTO);

        assertNotNull(result);

        assertEquals("Mbappé", result.getName());
        assertEquals("mbappe@teste.com", result.getEmail());
        assertEquals("11999999999", result.getPhone());
        assertEquals("12345678901", result.getCpf());
        assertEquals("hashed_password", result.getPasswordHash());
        assertEquals(UserStatus.CHECK_EMAIL, result.getStatus());

        assertTrue(result.getRoles().contains(responsibleRole));

        verify(userRepository, times(1))
                .findByCpf("12345678901");

        verify(passwordEncoder, times(1))
                .encode("championsLeague");

        verify(rolesRepository, times(1))
                .findByNome("ROLE_RESPONSIBLE");

        verify(userRepository, times(1))
                .save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenCpfAlreadyExists() {

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        UserOrDriverOrResponsibleAlreadyRegisteredException exception =
                assertThrows(
                        UserOrDriverOrResponsibleAlreadyRegisteredException.class,
                        () -> userService.createUser(defaultUserCreateDTO)
                );

        assertEquals("Usuario ja cadastrado", exception.getMessage());

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
        verify(rolesRepository, never()).findByNome(anyString());
    }

    @Test
    void shouldCreateResponsibleRoleWhenRoleIsNull() {

        defaultUserCreateDTO.setRole(null);

        RolesEntity role = RolesEntity.builder()
                .nome("ROLE_RESPONSIBLE")
                .build();

        when(userRepository.findByCpf(anyString()))
                .thenReturn(Optional.empty());

        when(rolesRepository.findByNome("ROLE_RESPONSIBLE"))
                .thenReturn(Optional.of(role));

        when(passwordEncoder.encode(anyString()))
                .thenReturn("hashed_password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(defaultUserCreateDTO);

        assertNotNull(result);
        assertTrue(result.getRoles().contains(role));

        verify(rolesRepository)
                .findByNome("ROLE_RESPONSIBLE");
    }

    @Test
    void shouldCreateRoleWhenRoleDoesNotExist() {

        when(userRepository.findByCpf(anyString()))
                .thenReturn(Optional.empty());

        when(rolesRepository.findByNome("ROLE_RESPONSIBLE"))
                .thenReturn(Optional.empty());

        when(rolesRepository.save(any(RolesEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(passwordEncoder.encode(anyString()))
                .thenReturn("hashed_password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(defaultUserCreateDTO);

        assertNotNull(result);

        ArgumentCaptor<RolesEntity> roleCaptor =
                ArgumentCaptor.forClass(RolesEntity.class);

        verify(rolesRepository).save(roleCaptor.capture());

        assertEquals(
                "ROLE_RESPONSIBLE",
                roleCaptor.getValue().getAuthority()
        );

        assertTrue(result.getRoles().contains(roleCaptor.getValue()));
    }

    @Test
    void shouldEncodePasswordBeforeSavingUser() {

        when(userRepository.findByCpf(anyString()))
                .thenReturn(Optional.empty());

        when(rolesRepository.findByNome(anyString()))
                .thenReturn(Optional.of(responsibleRole));

        when(passwordEncoder.encode("championsLeague"))
                .thenReturn("hashed_password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(defaultUserCreateDTO);

        assertEquals("hashed_password", result.getPasswordHash());

        verify(passwordEncoder)
                .encode("championsLeague");
    }

    // =========================================================
    // SEND CODE TO USER
    // =========================================================

    @Test
    void shouldSendCodeToUserSuccessfully() {

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        userService.sendCodeToUser("12345678901");

        verify(userRepository)
                .findByCpf("12345678901");

        verify(emailVerificationService)
                .generateAndSendCode(
                        "User:SendCodeUser",
                        "mbappe@teste.com"
                );
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExistWhileSendingCode() {

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> userService.sendCodeToUser("12345678901")
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verify(emailVerificationService, never())
                .generateAndSendCode(anyString(), anyString());
    }

    // =========================================================
    // UPDATE USER
    // =========================================================

    @Test
    void shouldUpdateEmailSuccessfully() {

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("novoemail@teste.com");
        updateUser.setCode("123456");

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        doNothing().when(emailVerificationService)
                .verifyCode("User:SendCodeUser", "123456");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String result =
                userService.updateUser(
                        updateUser,
                        "12345678901"
                );

        assertEquals(
                "User updated successfully",
                result
        );

        assertEquals(
                "novoemail@teste.com",
                defaultUser.getEmail()
        );

        verify(emailVerificationService)
                .verifyCode("User:SendCodeUser", "123456");

        verify(userRepository)
                .save(defaultUser);
    }

    @Test
    void shouldUpdatePhoneSuccessfully() {

        UpdateUser updateUser = new UpdateUser();
        updateUser.setPhone("11888888888");
        updateUser.setCode("123456");

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        doNothing().when(emailVerificationService)
                .verifyCode("User:SendCodeUser", "123456");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String result =
                userService.updateUser(
                        updateUser,
                        "12345678901"
                );

        assertEquals(
                "User updated successfully",
                result
        );

        assertEquals(
                "11888888888",
                defaultUser.getPhone()
        );

        verify(userRepository)
                .save(defaultUser);
    }

    @Test
    void shouldUpdateEmailAndPhoneSuccessfully() {

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("novoemail@teste.com");
        updateUser.setPhone("11888888888");
        updateUser.setCode("123456");

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        doNothing().when(emailVerificationService)
                .verifyCode("User:SendCodeUser", "123456");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String result =
                userService.updateUser(
                        updateUser,
                        "12345678901"
                );

        assertEquals(
                "User updated successfully",
                result
        );

        assertEquals(
                "novoemail@teste.com",
                defaultUser.getEmail()
        );

        assertEquals(
                "11888888888",
                defaultUser.getPhone()
        );

        verify(userRepository)
                .save(defaultUser);
    }

    @Test
    void shouldNotUpdateEmailWhenEmailIsNull() {

        String oldEmail = defaultUser.getEmail();

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail(null);
        updateUser.setPhone("11888888888");
        updateUser.setCode("123456");

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        doNothing().when(emailVerificationService)
                .verifyCode(anyString(), anyString());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateUser(
                updateUser,
                "12345678901"
        );

        assertEquals(
                oldEmail,
                defaultUser.getEmail()
        );

        assertEquals(
                "11888888888",
                defaultUser.getPhone()
        );
    }

    @Test
    void shouldNotUpdatePhoneWhenPhoneIsNull() {

        String oldPhone = defaultUser.getPhone();

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("novoemail@teste.com");
        updateUser.setPhone(null);
        updateUser.setCode("123456");

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        doNothing().when(emailVerificationService)
                .verifyCode(anyString(), anyString());

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateUser(
                updateUser,
                "12345678901"
        );

        assertEquals(
                oldPhone,
                defaultUser.getPhone()
        );

        assertEquals(
                "novoemail@teste.com",
                defaultUser.getEmail()
        );
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExistWhileUpdating() {

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("novoemail@teste.com");
        updateUser.setCode("123456");

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> userService.updateUser(
                                updateUser,
                                "12345678901"
                        )
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verify(emailVerificationService, never())
                .verifyCode(anyString(), anyString());

        verify(userRepository, never())
                .save(any(User.class));
    }

    // =========================================================
    // FIND BY ID
    // =========================================================

    @Test
    void shouldFindUserById() {

        UUID id = UUID.randomUUID();

        when(userRepository.findById(id))
                .thenReturn(Optional.of(defaultUser));

        Optional<User> result =
                userService.findById(id);

        assertTrue(result.isPresent());
        assertEquals(
                defaultUser,
                result.get()
        );

        verify(userRepository)
                .findById(id);
    }

    @Test
    void shouldReturnEmptyWhenUserIdDoesNotExist() {

        UUID id = UUID.randomUUID();

        when(userRepository.findById(id))
                .thenReturn(Optional.empty());

        Optional<User> result =
                userService.findById(id);

        assertTrue(result.isEmpty());

        verify(userRepository)
                .findById(id);
    }

    // =========================================================
    // DELETE USER
    // =========================================================

    @Test
    void shouldDeleteUserSuccessfully() {

        UUID id = UUID.randomUUID();
        String code = "123456";

        doNothing().when(emailVerificationService)
                .verifyCode("User:SendCodeUser", code);

        doNothing().when(userRepository)
                .deleteById(id);

        userService.deleteById(id, code);

        verify(emailVerificationService)
                .verifyCode("User:SendCodeUser", code);

        verify(userRepository)
                .deleteById(id);
    }

    @Test
    void shouldNotDeleteUserWhenCodeIsInvalid() {

        UUID id = UUID.randomUUID();
        String code = "wrong-code";

        doThrow(new RuntimeException("Invalid code"))
                .when(emailVerificationService)
                .verifyCode("User:SendCodeUser", code);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> userService.deleteById(id, code)
                );

        assertEquals(
                "Invalid code",
                exception.getMessage()
        );

        verify(userRepository, never())
                .deleteById(any(UUID.class));
    }

    // =========================================================
    // FIND BY CPF
    // =========================================================

    @Test
    void shouldFindUserByCpf() {

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.of(defaultUser));

        Optional<User> result =
                userService.findByCpf("12345678901");

        assertTrue(result.isPresent());
        assertEquals(
                defaultUser,
                result.get()
        );

        verify(userRepository)
                .findByCpf("12345678901");
    }

    @Test
    void shouldReturnEmptyWhenCpfDoesNotExist() {

        when(userRepository.findByCpf("12345678901"))
                .thenReturn(Optional.empty());

        Optional<User> result =
                userService.findByCpf("12345678901");

        assertTrue(result.isEmpty());

        verify(userRepository)
                .findByCpf("12345678901");
    }

    // =========================================================
    // FIND BY EMAIL
    // =========================================================

    @Test
    void shouldFindUserByEmail() {

        when(userRepository.findByEmail("mbappe@teste.com"))
                .thenReturn(Optional.of(defaultUser));

        Optional<User> result =
                userService.findByEmail("mbappe@teste.com");

        assertTrue(result.isPresent());
        assertEquals(
                defaultUser,
                result.get()
        );

        verify(userRepository)
                .findByEmail("mbappe@teste.com");
    }

    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {

        when(userRepository.findByEmail("naoexiste@teste.com"))
                .thenReturn(Optional.empty());

        Optional<User> result =
                userService.findByEmail("naoexiste@teste.com");

        assertTrue(result.isEmpty());

        verify(userRepository)
                .findByEmail("naoexiste@teste.com");
    }

    // =========================================================
    // SAVE USER
    // =========================================================

    @Test
    void shouldSaveUserSuccessfully() {

        when(userRepository.save(defaultUser))
                .thenReturn(defaultUser);

        userService.saveUser(defaultUser);

        verify(userRepository, times(1))
                .save(defaultUser);
    }
}