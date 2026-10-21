package br.com.vanroute.backend.services;

import br.com.vanroute.backend.services.CrlvExtractionService.CrlvData;
import br.com.vanroute.backend.services.CrlvExtractionService.TextSegment;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrlvExtractionServiceTest {

    // monta o CRLV como blocos de texto com posicao: rotulo em (x, y) e valor logo abaixo (x, y + 10)
    private static void field(List<TextSegment> segments, String label, String value, float x, float y) {
        segments.add(new TextSegment(label, x, y));
        segments.add(new TextSegment(value, x, y + 10));
    }

    private static List<TextSegment> fakeCrlv() {
        List<TextSegment> s = new ArrayList<>();
        field(s, "CÓDIGO RENAVAM", "12345678901", 20, 100);
        field(s, "PLACA", "ABC1D23", 20, 130);
        field(s, "EXERCÍCIO", "2026", 120, 130);
        field(s, "ANO FABRICAÇÃO", "2019", 20, 160);
        field(s, "ANO MODELO", "2020", 120, 160);
        field(s, "MARCA / MODELO / VERSÃO", "M.BENZ/SPRINTER 415", 20, 190);
        field(s, "PLACA ANTERIOR / UF", "*******/**", 20, 220);
        field(s, "CHASSI", "9BM906133LB000000", 160, 220);
        field(s, "COR PREDOMINANTE", "BRANCA", 20, 250);
        field(s, "COMBUSTÍVEL", "DIESEL", 120, 250);
        field(s, "CATEGORIA", "ALUGUEL", 300, 100);
        field(s, "LOTAÇÃO", "16P", 450, 130);
        return s;
    }

    @Test
    void shouldReadVehicleFieldsBelowEachLabel() {
        CrlvData data = CrlvExtractionService.parse(fakeCrlv());

        assertEquals("ABC1D23", data.plate());
        assertEquals("12345678901", data.renavam());
        assertEquals("9BM906133LB000000", data.chassis());
        assertEquals("M.BENZ/SPRINTER 415", data.model());
        assertEquals(2019, data.manufactureYear());
        assertEquals(2020, data.modelYear());
        assertEquals("BRANCA", data.color());
        assertEquals("DIESEL", data.fuel());
        assertEquals("ALUGUEL", data.category());
        assertEquals(2026, data.licensingYear());
        assertEquals(16, data.passengers());
    }

    @Test
    void shouldRejectDocumentWithoutPlateOrRenavam() {
        List<TextSegment> segments = new ArrayList<>();
        field(segments, "CHASSI", "9BM906133LB000000", 20, 100);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> CrlvExtractionService.parse(segments));
        assertEquals(422, e.getStatusCode().value());
    }
}
