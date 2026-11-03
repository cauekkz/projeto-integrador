package br.com.vanroute.backend.controllers;

import br.com.vanroute.backend.services.CnhParser;
import br.com.vanroute.backend.services.TermsService;
import java.time.LocalDate;

import br.com.vanroute.backend.models.user.Driver;
import br.com.vanroute.backend.models.user.User;
import br.com.vanroute.backend.services.DocumentOcrExtractionService;
import br.com.vanroute.backend.services.DriverService;
import br.com.vanroute.backend.services.EmailVerificationService;
import br.com.vanroute.backend.services.IcpValidationService;
import org.junit.jupiter.api.BeforeEach;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private br.com.vanroute.backend.services.TermsService termsService;

    @InjectMocks
    private DriverController driverController;

    private MockMultipartFile documentPdf;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(driverController)
                .setControllerAdvice(new br.com.vanroute.backend.exceptions.handler.CustomEntityResponseHandler()).build();

        documentPdf = new MockMultipartFile(
                "documentPdf",
                "cnh.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "There should be a law.".getBytes());
    }

    @Test
    void shouldCreateDriverSuccessfully() throws Exception {
        doNothing().when(icpValidationService).validateCnhSignature(any());
        when(documentOcrExtractionService.extractFromCnh(any())).thenReturn(cnh("NEYMAR JUNIOR", "AD"));

        User user = new User();
        user.setEmail("neymar@teste.com");
        Driver mockedDriver = new Driver();
        mockedDriver.setUser(user);
        when(driverService.createDriver(any(), anyString())).thenReturn(mockedDriver);

        mockMvc.perform(multipart("/api/driver/auth/signup")
                .file(documentPdf)
                .param("name", "Neymar Junior")
                .param("email", "neymar@teste.com")
                .param("password", "Hexa2026123!")
                .param("confirmPassword", "Hexa2026123!")
                .param("phone", "11999999999")
                .param("acceptedTermsVersion", TermsService.CURRENT_VERSION)
                // .param("driverType", "B")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated());

        verify(icpValidationService, times(1)).validateCnhSignature(any());
        verify(documentOcrExtractionService, times(1)).extractFromCnh(any());
        verify(driverService, times(1)).createDriver(any(), anyString());
        verify(emailVerificationService, times(1)).sendCodeAfterSignup(
                eq("verificationEmail:email:neymar@teste.com"),
                eq("neymar@teste.com")
        );
    }

    // CNH lida pelo OCR: CPF valido, 34 anos, valida ate 2035
    private static CnhParser.CnhData cnh(String name, String category) {
        return new CnhParser.CnhData("52998224725", name, false, LocalDate.of(1992, 2, 5), LocalDate.of(2035, 1, 1), category);
    }

    private void signupWith(CnhParser.CnhData data, int expectedStatus) throws Exception {
        doNothing().when(icpValidationService).validateCnhSignature(any());
        when(documentOcrExtractionService.extractFromCnh(any())).thenReturn(data);

        mockMvc.perform(multipart("/api/driver/auth/signup")
                .file(documentPdf)
                .param("name", "Neymar Junior")
                .param("email", "neymar@teste.com")
                .param("password", "Hexa2026123!")
                .param("confirmPassword", "Hexa2026123!")
                .param("phone", "11999999999")
                .param("acceptedTermsVersion", TermsService.CURRENT_VERSION)
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void nameDifferentFromTheCnhIsRefused() throws Exception {
        signupWith(cnh("OUTRA PESSOA", "AD"), 422);
        verify(driverService, never()).createDriver(any(), anyString());
    }

    @Test
    void cnhWithoutCategoryDIsRefused() throws Exception {
        signupWith(cnh("NEYMAR JUNIOR", "B"), 422);
        verify(driverService, never()).createDriver(any(), anyString());
    }

    @Test
    void shouldReturnUnprocessableEntityWhenSignatureIsInvalid() throws Exception {
        doThrow(new RuntimeException("Assinatura inválida")).when(icpValidationService).validateCnhSignature(any());

        mockMvc.perform(multipart("/api/driver/auth/signup")
                .file(documentPdf)
                .param("name", "Neymar Junior")
                .param("email", "neymar@teste.com")
                .param("cnhNumber", "123456789")
                .param("password", "Hexa2026123!")
                .param("confirmPassword", "Hexa2026123!")
                .param("phone", "11999999999")
                .param("acceptedTermsVersion", TermsService.CURRENT_VERSION)
                // .param("driverType", "B")
                .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnprocessableEntity());

        verify(icpValidationService, times(1)).validateCnhSignature(any());
        verify(documentOcrExtractionService, never()).extractFromCnh(any());
        verify(driverService, never()).createDriver(any(), anyString());
        verify(emailVerificationService, never()).sendCodeAfterSignup(any(), any());
    }

    @Test
    void shouldReturnLinkCodeOfAuthenticatedDriver() throws Exception {
        UUID userId = UUID.randomUUID();
        when(driverService.getLinkCode(userId)).thenReturn("ABC23XYZ9");

        mockMvc.perform(get("/api/driver/link-code")
                .principal(new UsernamePasswordAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkCode").value("ABC23XYZ9"));

        verify(driverService, times(1)).getLinkCode(userId);
    }

}
