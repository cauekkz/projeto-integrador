package br.com.vanroute.backend.services;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

public class IcpValidationServiceTest {
    ClassPathResource invalidCNH = new ClassPathResource("documents/invalidCNH.pdf");
    ClassPathResource validCNH = new ClassPathResource("documents/validCNH.pdf");
    ClassPathResource invalidFileFormater = new ClassPathResource("documents/invalidFileFormater.html"); 

   // byte[] bytes = invalidCNH.getInputStream().readAllBytes();
    //MockMultipartFile invalidCNHFile = new MockMultipartFile("documentPdf", "invalidCNH.pdf", "application/pdf", bytes);
    
    @Test
    void shouldThrowExceptionWhenCNHIsInvalid() {
        
    }
}
