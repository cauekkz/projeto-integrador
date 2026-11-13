package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.BadRequestException;
import br.com.vanroute.backend.models.user.TermsAcceptance;
import br.com.vanroute.backend.repositories.TermsAcceptanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    @Mock
    private TermsAcceptanceRepository repository;

    private final UUID userId = UUID.randomUUID();

    @Test
    void currentVersionComesWithTheText() {
        TermsService.Terms terms = new TermsService(repository).current();

        assertEquals(TermsService.CURRENT_VERSION, terms.version());
        assertTrue(terms.content().contains("Política de Privacidade"));
    }

    @Test
    void acceptingRecordsWhoWhichVersionAndWhen() {
        new TermsService(repository).accept(userId, TermsService.CURRENT_VERSION);

        verify(repository).save(argThat((TermsAcceptance a) -> a.getUserId().equals(userId)
                && a.getVersion().equals(TermsService.CURRENT_VERSION) && a.getAcceptedAt() != null));
    }

    @Test
    void acceptingTwiceDoesNotDuplicate() {
        when(repository.existsByUserIdAndVersion(userId, TermsService.CURRENT_VERSION)).thenReturn(true);

        new TermsService(repository).accept(userId, TermsService.CURRENT_VERSION);

        verify(repository, never()).save(any());
    }

    @Test
    void onlyTheCurrentVersionCanBeAccepted() {
        TermsService service = new TermsService(repository);

        assertThrows(BadRequestException.class, () -> service.accept(userId, "0"));
        assertThrows(BadRequestException.class, () -> service.accept(userId, null));
        verify(repository, never()).save(any());
    }

    @Test
    void newVersionAsksEveryoneAgain() {
        // quem so aceitou a versao 1 nao tem aceite da atual (o service so consulta a atual): o filtro bloqueia
        // ate aceitar de novo
        TermsService service = new TermsService(repository);
        when(repository.existsByUserIdAndVersion(userId, TermsService.CURRENT_VERSION)).thenReturn(false);

        assertEquals("3", TermsService.CURRENT_VERSION);
        assertFalse(service.acceptedCurrent(userId));
        assertThrows(BadRequestException.class, () -> service.accept(userId, "1"));
        assertThrows(BadRequestException.class, () -> service.requireCurrent("1"));
    }

    @Test
    void textOfTheCurrentVersionFollowsTheCode() {
        String text = new TermsService(repository).current().content();

        assertTrue(text.contains("versão 3"));
        assertFalse(text.contains("RASCUNHO"));
        // regras que o codigo aplica: aviso previo, divida que nao some, CNH nao guardada, operadores
        assertTrue(text.contains("30 dias depois do pedido"));
        assertTrue(text.contains("não apaga dívidas"));
        assertTrue(text.contains("não são guardados"));
        assertTrue(text.contains("OpenRouteService"));
        // foto do aluno: quem ve
        assertTrue(text.contains("foto do aluno"));
    }
}
