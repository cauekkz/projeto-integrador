package br.com.vanroute.backend.services;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    void shouldSendEmailWithExpectedFields() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailService.sendEmail("user@example.com", "Assunto teste", "Seu código de verificação é: 123456");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        MimeMessage sent = messageCaptor.getValue();
        sent.saveChanges();
        assertEquals("user@example.com", sent.getAllRecipients()[0].toString());
        assertEquals("Assunto teste", sent.getSubject());

        List<Part> partes = new ArrayList<>();
        juntarPartes(sent, partes);
        String texto = partes.stream().filter(p -> tipo(p, "text/plain")).map(this::conteudo).findFirst().orElseThrow();
        String html = partes.stream().filter(p -> tipo(p, "text/html")).map(this::conteudo).findFirst().orElseThrow();
        // texto puro igual ao corpo; HTML com o codigo no quadro e a logo embutida
        assertEquals("Seu código de verificação é: 123456", texto);
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("cid:logo"));
        assertTrue(partes.stream().anyMatch(p -> tipo(p, "image/png")));
    }

    @Test
    void htmlKeepsTheExplanationWithoutTheCodeCallout() {
        String html = emailService.montarHtml("Assunto",
                "Houve muitas tentativas erradas com o código anterior, então ele foi trocado. Seu novo código é: 654321");

        assertTrue(html.contains("então ele foi trocado."));
        assertFalse(html.contains("Seu novo código é"));
        assertTrue(html.contains("654321"));
        assertFalse(html.contains("{{"));
    }

    private void juntarPartes(Part parte, List<Part> partes) throws Exception {
        if (parte.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart filha = multipart.getBodyPart(i);
                juntarPartes(filha, partes);
            }
        } else {
            partes.add(parte);
        }
    }

    private boolean tipo(Part p, String mime) {
        try {
            return p.isMimeType(mime);
        } catch (Exception e) {
            return false;
        }
    }

    private String conteudo(Part p) {
        try {
            return String.valueOf(p.getContent());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
