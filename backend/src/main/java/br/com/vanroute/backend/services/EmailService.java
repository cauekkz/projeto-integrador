package br.com.vanroute.backend.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

// e-mail com a cara do VanRoute: HTML (logo, codigo em destaque) + o mesmo texto puro junto,
// pra quem le sem HTML. O corpo continua sendo um texto so; quando ele termina num codigo de 6 digitos
// (os e-mails de verificacao), o codigo vai pro quadro em destaque do modelo
@Service
public class EmailService {

    private static final String MODELO = "email/codigo.html";
    private static final String LOGO = "email/logo.png";
    private static final Pattern TEXTO_E_CODIGO = Pattern.compile("^(.*?)[\\s:]*(\\d{6})\\s*$", Pattern.DOTALL);
    private static final Pattern CHAMADA_DO_CODIGO = Pattern.compile("\\s*Seu (novo )?código( de verificação)? é\\s*:?\\s*$");
    private static final String TEXTO_PADRAO = "Use o código abaixo para continuar no VanRoute.";

    private final JavaMailSender mailSender;
    private String modelo;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, montarHtml(subject, body));
            // a logo vai dentro do e-mail (cid:logo): o Gmail nao alcanca imagem em localhost
            helper.addInline("logo", new ClassPathResource(LOGO), "image/png");
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException("Não foi possível montar o e-mail", e);
        }
    }

    String montarHtml(String subject, String body) {
        Matcher m = TEXTO_E_CODIGO.matcher(body);
        String texto = m.matches() ? m.group(1).trim() : body.trim();
        String codigo = m.matches() ? m.group(2) : "";
        // "... Seu novo código é:" vira so a explicacao; o codigo aparece no quadro logo abaixo
        texto = CHAMADA_DO_CODIGO.matcher(texto).replaceFirst("").trim();
        if (texto.isEmpty()) {
            texto = TEXTO_PADRAO;
        } else if (!texto.endsWith(".")) {
            texto = texto + ".";
        }
        return carregarModelo()
                .replace("{{assunto}}", HtmlUtils.htmlEscape(subject, "UTF-8"))
                .replace("{{texto}}", HtmlUtils.htmlEscape(texto, "UTF-8"))
                .replace("{{codigo}}", codigo);
    }

    private String carregarModelo() {
        if (modelo == null) {
            try (var in = new ClassPathResource(MODELO).getInputStream()) {
                modelo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Modelo de e-mail não encontrado: " + MODELO, e);
            }
        }
        return modelo;
    }
}
