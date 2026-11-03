package br.com.vanroute.backend.services;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IcpValidationServiceTest {

    private final IcpValidationService icpValidationService = new IcpValidationService();

    // as CNHs de teste sao documentos reais (a assinatura ICP-Brasil nao da pra forjar), entao ficam fora do git:
    // cada um coloca as suas em src/test/resources/documents; sem o arquivo o teste e pulado
    private static byte[] privateDocument(String name) throws IOException {
        ClassPathResource resource = new ClassPathResource("documents/" + name);
        assumeTrue(resource.exists(), "documents/" + name + " nao esta na maquina (documento real, fora do git)");
        return resource.getInputStream().readAllBytes();
    }

    @Test
    void shouldValidateCnhSuccessfully() throws IOException {
        byte[] bytes = privateDocument("validCNH.pdf");
        MockMultipartFile file = new MockMultipartFile("documentPdf", "validCNH.pdf", "application/pdf", bytes);

        assertDoesNotThrow(() -> icpValidationService.validateCnhSignature(file));
    }

    @Test
    void shouldThrowExceptionWhenCNHIsInvalid() throws IOException {
        byte[] bytes = privateDocument("invalidCNH.pdf");
        MockMultipartFile file = new MockMultipartFile("documentPdf", "invalidCNH.pdf", "application/pdf", bytes);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> icpValidationService.validateCnhSignature(file));
        assertTrue(exception.getMessage().contains("Falha ao validar a assinatura digital ICP-Brasil"));
    }

    @Test
    void shouldThrowExceptionWhenFileFormatIsInvalid() throws IOException {
        ClassPathResource invalidFormat = new ClassPathResource("documents/invalidFileFormater.html");
        byte[] bytes = invalidFormat.getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("documentPdf", "invalidFileFormater.html", "text/html", bytes);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> icpValidationService.validateCnhSignature(file));
        assertTrue(exception.getMessage().contains("O arquivo enviado não é uma CNH digital"));

        RuntimeException exception2 = assertThrows(RuntimeException.class, () -> icpValidationService.validateCrlvSignature(file));
        assertTrue(exception2.getMessage().contains("O arquivo enviado não é um CRLV digital"));
    }
}
