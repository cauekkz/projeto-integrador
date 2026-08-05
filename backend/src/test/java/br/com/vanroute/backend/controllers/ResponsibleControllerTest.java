package br.com.vanroute.backend.controllers;

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

import static org.mockito.ArgumentMatchers.any;
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
        mockMvc = MockMvcBuilders.standaloneSetup(responsibleController).build();
    }

    @Test
    void shouldCreateResponsibleSuccessfully() throws Exception {
        RolesEntity role = RolesEntity.builder().nome("ROLE_RESPONSIBLE").build();
        ResponsibleResponseDTO response = new ResponsibleResponseDTO(
                "Carolina Souza",
                "carolina@example.com",
                "12345678901",
                "11999999999",
                FinancialStatus.PENDING,
                Set.of(role)
        );

        when(responsibleService.createResponsible(any())).thenReturn(response);

        mockMvc.perform(post("/api/responsible/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Carolina Souza",
                                  "email": "carolina@example.com",
                                  "password": "SenhaForte123!",
                                  "confirmPassword": "SenhaForte123!",
                                  "cpf": "12345678901",
                                  "phone": "11999999999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "name": "Carolina Souza",
                          "email": "carolina@example.com",
                          "cpf": "12345678901",
                          "phone": "11999999999",
                          "financialStatus": "PENDING"
                        }
                        """));

        verify(responsibleService, times(1)).createResponsible(any());
    }
}

