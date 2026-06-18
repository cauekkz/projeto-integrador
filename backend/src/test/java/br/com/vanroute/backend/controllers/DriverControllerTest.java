package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.services.DocumentOcrExtractionService;
import br.com.vanroute.backend.services.DriverService;
import br.com.vanroute.backend.services.IcpValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class DriverControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DriverService driverService;

    @Mock
    private IcpValidationService icpValidationService;

    @Mock
    private DocumentOcrExtractionService documentOcrExtractionService;

    @InjectMocks
    private DriverController driverController;

    private MockMultipartFile documentPdf;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(driverController).build();

        documentPdf = new MockMultipartFile(
                "documentPdf",
                "cnh.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "There should be a law.".getBytes()
        );
    }

    @Test
    void shouldCreateDriverSuccessfully() throws Exception {
        doNothing().when(icpValidationService).validateCnhSignature(any());
        when(documentOcrExtractionService.extractFromCnh(any())).thenReturn("12345678901");
        
        Driver mockedDriver = new Driver();
        when(driverService.createDriver(any(), anyString())).thenReturn(mockedDriver);

        mockMvc.perform(multipart("/api/drivers/signup")
                        .file(documentPdf)
                        .param("name", "Neymar Junior")
                        .param("email", "neymar@teste.com")
                        .param("password", "Hexa2026123!")
                        .param("confirmPassword", "Hexa2026123!")
                        .param("phone", "11999999999")
                        //.param("driverType", "B")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());

        verify(icpValidationService, times(1)).validateCnhSignature(any());
        verify(documentOcrExtractionService, times(1)).extractFromCnh(any());
        verify(driverService, times(1)).createDriver(any(), anyString());
    }

    @Test
    void shouldReturnUnprocessableEntityWhenSignatureIsInvalid() throws Exception {
        doThrow(new RuntimeException("Assinatura inválida")).when(icpValidationService).validateCnhSignature(any());

        mockMvc.perform(multipart("/api/drivers/signup")
                        .file(documentPdf)
                        .param("name", "Neymar Junior")
                        .param("email", "neymar@teste.com")
                        .param("cnhNumber", "123456789")
                        .param("password", "Hexa2026123!")
                        .param("confirmPassword", "Hexa2026123!")
                        .param("phone", "11999999999")
                        //.param("driverType", "B")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnprocessableEntity());

        verify(icpValidationService, times(1)).validateCnhSignature(any());
        verify(documentOcrExtractionService, never()).extractFromCnh(any());
        verify(driverService, never()).createDriver(any(), anyString());
    }


}
