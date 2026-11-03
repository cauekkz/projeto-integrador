package br.com.vanroute.backend.services;

import br.com.vanroute.backend.dtos.user.ResponsibleRequestDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.dtos.user.UserCreateDTO;
import br.com.vanroute.backend.models.user.Responsible;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.models.user.enums.RoleTypeEnum;
import br.com.vanroute.backend.repositories.ResponsibleRepository;
import br.com.vanroute.backend.dtos.student.AddressRequestDTO;
import br.com.vanroute.backend.models.address.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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

    @Mock
    private AddressService addressService;

    @Mock
    private MapService mapService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private TermsService termsService;

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
        requestDTO.setAcceptedTermsVersion("1");
        requestDTO.setAddressRequestDTO(new AddressRequestDTO("Avenida Paulista", "01310-100", "São Paulo", "Bela Vista", 1000, "SP", null, null));
    }

    @Test
    void shouldCreateResponsibleSuccessfully() {
        User createdUser = new User();
        createdUser.setName(requestDTO.getName());
        createdUser.setEmail(requestDTO.getEmail());
        createdUser.setCpf(requestDTO.getCpf());
        createdUser.setPhone(requestDTO.getPhone());
        createdUser.setRole(RoleTypeEnum.ROLE_RESPONSIBLE);

        when(userService.createUser(any(UserCreateDTO.class))).thenReturn(createdUser);
        Address address = new Address();
        address.setStreet("Avenida Paulista");
        // o AddressService devolve o endereco ja com a coordenada (novo ou reaproveitado)
        address.setLatitude(-23.56);
        address.setLongitude(-46.65);
        when(addressService.findOrCreate(any())).thenReturn(address);

        Responsible savedResponsible = new Responsible();
        savedResponsible.setUser(createdUser);
        when(responsibleRepository.save(any(Responsible.class))).thenReturn(savedResponsible);

        ResponsibleResponseDTO response = responsibleService.createResponsible(requestDTO);

        assertNotNull(response);
        assertEquals(requestDTO.getName(), response.name());
        assertEquals(requestDTO.getEmail(), response.email());
        assertEquals(requestDTO.getCpf(), response.cpf());
        assertEquals(requestDTO.getPhone(), response.phone());
        org.mockito.Mockito.verify(responsibleRepository).save(any(Responsible.class));
        assertEquals(List.of("ROLE_RESPONSIBLE"), response.roles());

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
        verify(emailVerificationService, times(1))
                .sendCodeAfterSignup("verificationEmail:email:carolina@example.com", "carolina@example.com");
        assertEquals(-23.56, response.address().latitude());
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

