package br.com.vanroute.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.exceptions.UserOrDriverOrResponsibleAlreadyRegisteredException;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.DriverApprovalStatus;
import br.com.vanroute.backend.repositories.DriverRepository;
import br.com.vanroute.backend.utils.CodeGenerator;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @InjectMocks
    private DriverService driverService;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private UserService userService;

    @Mock
    private CodeGenerator codeGenerator;

    private DriverRequestDTO generateDriverRequestDTO() {
        DriverRequestDTO dto = new DriverRequestDTO();
        dto.setName("Harry Kane");
        dto.setEmail("harry@example.com");
        dto.setPassword("england123!");
        dto.setPhone("11999999999");
        return dto;
    }

    @Test
    void shouldCreateDriverSuccessfully() {
        String cpf = "12345678900";
        DriverRequestDTO requestDTO = generateDriverRequestDTO();

        User user = new User();
        user.setCpf(cpf);
        user.setName(requestDTO.getName());
        user.setEmail(requestDTO.getEmail());

        when(userService.createUser(any(UserCreateDTO.class))).thenReturn(user);
        when(codeGenerator.generateCode()).thenReturn("ABC123XYZ");

        Driver mockedDriver = new Driver();
        mockedDriver.setUser(user);
        mockedDriver.setApprovalStatus(DriverApprovalStatus.APPROVED);

        when(driverRepository.saveAndFlush(any(Driver.class))).thenReturn(mockedDriver);

        Driver result = driverService.createDriver(requestDTO, cpf);

        assertNotNull(result);
        assertEquals(user, result.getUser());
        assertEquals(DriverApprovalStatus.APPROVED, result.getApprovalStatus());
        verify(userService, times(1)).createUser(any(UserCreateDTO.class));
        verify(driverRepository, times(1)).saveAndFlush(any(Driver.class));
    }

    @Test
    void shouldThrowExceptionWhenUserAlreadyExists() {
        String cpf = "12345678900";
        DriverRequestDTO requestDTO = generateDriverRequestDTO();

        when(userService.createUser(any(UserCreateDTO.class)))
                .thenThrow(new UserOrDriverOrResponsibleAlreadyRegisteredException("Usuario/Motorista já cadastrado"));

        Exception exception = assertThrows(UserOrDriverOrResponsibleAlreadyRegisteredException.class, () -> {
            driverService.createDriver(requestDTO, cpf);
        });

        assertEquals("Usuario/Motorista já cadastrado", exception.getMessage());
        verify(userService, times(1)).createUser(any(UserCreateDTO.class));
        verify(driverRepository, never()).saveAndFlush(any(Driver.class));
    }
}