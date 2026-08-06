package br.com.vanroute.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.vanroute.backend.dtos.user.DriverRequestDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.DriverApprovalStatus;
import br.com.vanroute.backend.repositories.DriverRepository;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @InjectMocks
    private DriverService driverService;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private UserService userService;

    private DriverRequestDTO generateDriverRequestDTO() {
        DriverRequestDTO dto = new DriverRequestDTO();
        dto.setName("Harry Kane");
        dto.setEmail("harry@example.com");
        dto.setPassword("england123!");
        dto.setPhone("11999999999");
        //dto.setDriverType("OWNER");
        return dto;
    }

    @Test
    void shouldCreateDriverSuccessfully() {
        String cpf = "12345678900";
        DriverRequestDTO requestDTO = generateDriverRequestDTO();

        when(userService.findByCpf(cpf)).thenReturn(Optional.empty());

        User user = new User();
        user.setCpf(cpf);   
        user.setName(requestDTO.getName());
        user.setEmail(requestDTO.getEmail());
        
        when(userService.createUser(any(UserCreateDTO.class))).thenReturn(user);

        Driver mockedDriver = new Driver();
        mockedDriver.setUser(user);
        mockedDriver.setApprovalStatus(DriverApprovalStatus.APPROVED);

        when(driverRepository.save(any(Driver.class))).thenReturn(mockedDriver);

        Driver result = driverService.createDriver(requestDTO, cpf);

        assertNotNull(result);
        assertEquals(user, result.getUser());
        assertEquals(DriverApprovalStatus.APPROVED, result.getApprovalStatus());
        verify(userService, times(1)).findByCpf(cpf);
        verify(userService, times(1)).createUser(any(UserCreateDTO.class));
        verify(driverRepository, times(1)).save(any(Driver.class));
    }

    @Test
    void shouldThrowExceptionWhenUserAlreadyExists() {
        String cpf = "12345678900";
        DriverRequestDTO requestDTO = generateDriverRequestDTO();

        when(userService.findByCpf(cpf)).thenReturn(Optional.of(new User()));


        Exception exception = assertThrows(RuntimeException.class, () -> {
            driverService.createDriver(requestDTO, cpf);
        });

        assertEquals("Usuario/Motorista já cadastrado", exception.getMessage());
        verify(userService, times(1)).findByCpf(cpf);
        verify(userService, never()).createUser(any(UserCreateDTO.class));
        verify(driverRepository, never()).save(any(Driver.class));
    }
        
}
