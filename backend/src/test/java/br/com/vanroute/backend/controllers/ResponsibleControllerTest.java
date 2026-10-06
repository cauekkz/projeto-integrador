package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.dtos.route.AddressResponseDTO;
import br.com.vanroute.backend.dtos.user.ResponsibleResponseDTO;
import br.com.vanroute.backend.models.user.RolesEntity;
import br.com.vanroute.backend.models.user.enums.FinancialStatus;
import br.com.vanroute.backend.services.EmailVerificationService;
import br.com.vanroute.backend.services.ResponsibleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResponsibleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ResponsibleService responsibleService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private ResponsibleController responsibleController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(responsibleController)
                .build();
    }

    @Test
    void shouldCreateResponsibleSuccessfully() throws Exception {
        UUID userId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        RolesEntity role = RolesEntity.builder()
                .nome("ROLE_RESPONSIBLE")
                .build();

        AddressResponseDTO address = new AddressResponseDTO(
                userId,
                "PAULISTA",
                "01310-100",
                "São Paulo",
                "Bela Vista",
                1000,
                "SP",
                -23.561684,
                -46.656139
        );

        ResponsibleResponseDTO response = new ResponsibleResponseDTO(
                "Carolina Souza",
                "carolina@example.com",
                "12345678901",
                "11999999999",
                FinancialStatus.PENDING,
                Set.of(role),
                address
        );

        when(responsibleService.createResponsible(any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/responsible/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Carolina Souza",
                                  "email": "carolina@example.com",
                                  "password": "SenhaForte123!",
                                  "confirmPassword": "SenhaForte123!",
                                  "cpf": "12345678901",
                                  "phone": "11999999999",
                                  "address": {
                                    "street": "Avenida Paulista",
                                    "zipCode": "01310-100",
                                    "city": "São Paulo",
                                    "neighborhood": "Bela Vista",
                                    "number": 1000,
                                    "state": "SP"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "name": "Carolina Souza",
                          "email": "carolina@example.com",
                          "cpf": "12345678901",
                          "phone": "11999999999",
                          "financialStatus": "PENDING",
                          "address": {
                            "street": "Avenida Paulista",
                            "zipCode": "01310-100",
                            "city": "São Paulo",
                            "neighborhood": "Bela Vista",
                            "number": 1000,
                            "state": "SP",
                            "latitude": -23.561684,
                            "longitude": -46.656139
                          }
                        }
                        """));

        verify(responsibleService, times(1))
                .createResponsible(any());

        verify(emailVerificationService, times(1))
                .generateAndSendCode(
                        eq("verificationEmail:email:carolina@example.com"),
                        eq("carolina@example.com")
                );
    }
}