package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.ResponsibleRequestDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResponsibleServiceTest {

    @InjectMocks
    private ResponsibleService responsibleService;

    @Mock
    private ResponsibleRepository responsibleRepository;

    @Mock
    private UserService userService;

    private ResponsibleRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        requestDTO = new ResponsibleRequestDTO();
        requestDTO.setName("Carolina Souza");
        requestDTO.setEmail("carolina@example.com");
        requestDTO.setPassword("SenhaForte123!");
        requestDTO.setConfirmPassword("SenhaForte123!");
        requestDTO.setCpf("12345678901");
        requestDTO.setPhone("11999999999");
    }

    @Test
    void shouldCreateResponsibleSuccessfully() {
        RolesEntity role = RolesEntity.builder().nome(RoleTypeEnum.ROLE_RESPONSIBLE.name()).build();

        User createdUser = new User();
        createdUser.setName(requestDTO.getName());
        createdUser.setEmail(requestDTO.getEmail());
        createdUser.setCpf(requestDTO.getCpf());
        createdUser.setPhone(requestDTO.getPhone());
        createdUser.setRoles(Set.of(role));

        when(userService.createUser(any(UserCreateDTO.class))).thenReturn(createdUser);

        Responsible savedResponsible = new Responsible();
        savedResponsible.setUser(createdUser);
        savedResponsible.setFinancialStatus(FinancialStatus.PENDING);
        when(responsibleRepository.save(any(Responsible.class))).thenReturn(savedResponsible);

        ResponsibleResponseDTO response = responsibleService.createResponsible(requestDTO);

        assertNotNull(response);
        assertEquals(requestDTO.getName(), response.name());
        assertEquals(requestDTO.getEmail(), response.email());
        assertEquals(requestDTO.getCpf(), response.cpf());
        assertEquals(requestDTO.getPhone(), response.phone());
        assertEquals(FinancialStatus.PENDING, response.financialStatus());
        assertEquals(Set.of(role), response.roleTypeEnum());

        ArgumentCaptor<UserCreateDTO> userDtoCaptor = ArgumentCaptor.forClass(UserCreateDTO.class);
        verify(userService, times(1)).createUser(userDtoCaptor.capture());
        UserCreateDTO userDto = userDtoCaptor.getValue();
        assertEquals(requestDTO.getName(), userDto.getName());
        assertEquals(requestDTO.getCpf(), userDto.getCpf());
        assertEquals(requestDTO.getPassword(), userDto.getPasswordHash());
        assertEquals(requestDTO.getEmail(), userDto.getEmail());
        assertEquals(requestDTO.getPhone(), userDto.getPhone());
        assertEquals(RoleTypeEnum.ROLE_RESPONSIBLE, userDto.getRole());

        verify(responsibleRepository, times(1)).save(any(Responsible.class));
    }

    @Test
    void shouldPropagateExceptionWhenResponsibleAlreadyExists() {
        when(userService.createUser(any(UserCreateDTO.class)))
                .thenThrow(new RuntimeException("Usuario ja cadastrado"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> responsibleService.createResponsible(requestDTO));

        assertEquals("Usuario ja cadastrado", exception.getMessage());
        verify(responsibleRepository, never()).save(any(Responsible.class));
    }
}

