package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.user.TermsAcceptance;
import br.com.vanroute.backend.repositories.TermsAcceptanceRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

// Termos de Uso e Politica de Privacidade: o texto de cada versao fica em resources/terms/v{versao}.md.
// Versao nova = arquivo novo + mudar CURRENT_VERSION: todo mundo volta a ter termsAccepted=false no /users/me
@Service
public class TermsService {

    public static final String CURRENT_VERSION = "1";

    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final String currentText;

    public TermsService(TermsAcceptanceRepository termsAcceptanceRepository) {
        this.termsAcceptanceRepository = termsAcceptanceRepository;
        this.currentText = load(CURRENT_VERSION);
    }

    public record Terms(String version, String content) {
    }

    public Terms current() {
        return new Terms(CURRENT_VERSION, currentText);
    }

    public boolean acceptedCurrent(UUID userId) {
        return termsAcceptanceRepository.existsByUserIdAndVersion(userId, CURRENT_VERSION);
    }

    // cadastro: a versao aceita na tela tem que ser a atual (antes de criar a conta)
    public void requireCurrent(String version) {
        if (!CURRENT_VERSION.equals(version)) {
            throw new BadRequestException("Aceite a versão atual dos Termos de Uso (" + CURRENT_VERSION + ")");
        }
    }

    // aceitar de novo a mesma versao nao duplica
    @Transactional
    public void accept(UUID userId, String version) {
        if (!CURRENT_VERSION.equals(version)) {
            throw new BadRequestException("Aceite a versão atual dos termos (" + CURRENT_VERSION + ")");
        }
        if (!termsAcceptanceRepository.existsByUserIdAndVersion(userId, version)) {
            termsAcceptanceRepository.save(new TermsAcceptance(userId, version, LocalDateTime.now(TripService.ZONE)));
        }
    }

    private static String load(String version) {
        try {
            return new ClassPathResource("terms/v" + version + ".md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Texto dos termos v" + version + " não encontrado", e);
        }
    }
}
