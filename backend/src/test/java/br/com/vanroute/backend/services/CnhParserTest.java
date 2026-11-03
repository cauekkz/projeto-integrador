package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.InvalidDocumentException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// texto parecido com o que o OCR le da CNH digital (dados inventados; CPF de exemplo valido)
class CnhParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    private static String mrzDates(String birth, String expiry) {
        return birth + CnhParser.checkDigit(birth) + "M" + expiry + CnhParser.checkDigit(expiry) + "BRA<<<<<<<<<<<0";
    }

    private static String ocr(String category, String mrzName, String dates) {
        return """
                CARTEIRA NACIONAL DE HABILITAÇÃO / DRIVER LICENSE
                4d CPF 5 Nº REGISTRO 9 CAT HAB
                E N Ei ; | 529.982.247-25 | 0123456789] %s ha no mm
                I<BRA012345678<123<<<<<<<<<<<<
                %s
                %s
                """.formatted(category, dates, mrzName);
    }

    @Test
    void readsEverythingFromTheMachineReadableZone() {
        CnhParser.CnhData cnh = CnhParser.parse(ocr("AD", "JOAO<<DA<SILVA<<<<<<<<<<<<<<<<", mrzDates("900514", "330101")));

        assertEquals("52998224725", cnh.cpf());
        assertEquals("JOAO DA SILVA", cnh.name());
        assertFalse(cnh.nameTruncated());
        assertEquals(LocalDate.of(1990, 5, 14), cnh.birthDate());
        assertEquals(LocalDate.of(2033, 1, 1), cnh.expiryDate());
        assertEquals("AD", cnh.category());
        assertTrue(cnh.complete());
        assertDoesNotThrow(() -> CnhParser.check(cnh, "João da Silva", TODAY));
    }

    @Test
    void wrongCheckDigitMeansTheDatesWereNotRead() {
        String broken = "9005141M3301015BRA<<<<<<<<<<<0";
        CnhParser.CnhData cnh = CnhParser.parse(ocr("D", "JOAO<<SILVA<<<<<<<<<<<<<<<<<<<", broken));

        assertNull(cnh.birthDate());
        assertNull(cnh.expiryDate());
        assertThrows(InvalidDocumentException.class, () -> CnhParser.check(cnh, "Joao Silva", TODAY));
    }

    @Test
    void cpfWithWrongDigitsIsIgnored() {
        String text = ocr("D", "JOAO<<SILVA<<<<<<<<<<<<<<<<<<<", mrzDates("900514", "330101")).replace("529.982.247-25", "529.982.247-26");

        assertNull(CnhParser.parse(text).cpf());
    }

    @Test
    void rulesOfTheSchoolDriver() {
        String name = "JOAO<<SILVA<<<<<<<<<<<<<<<<<<<";
        // nome diferente do da CNH
        assertThrows(InvalidDocumentException.class,
                () -> CnhParser.check(CnhParser.parse(ocr("D", name, mrzDates("900514", "330101"))), "Pedro Souza", TODAY));
        // vencida
        assertThrows(InvalidDocumentException.class,
                () -> CnhParser.check(CnhParser.parse(ocr("D", name, mrzDates("900514", "260101"))), "Joao Silva", TODAY));
        // 20 anos
        assertThrows(InvalidDocumentException.class,
                () -> CnhParser.check(CnhParser.parse(ocr("D", name, mrzDates("060514", "330101"))), "Joao Silva", TODAY));
        // categoria B
        assertThrows(InvalidDocumentException.class,
                () -> CnhParser.check(CnhParser.parse(ocr("AB", name, mrzDates("900514", "330101"))), "Joao Silva", TODAY));
        // E tambem serve (inclui a D)
        assertDoesNotThrow(() -> CnhParser.check(CnhParser.parse(ocr("E", name, mrzDates("900514", "330101"))), "Joao Silva", TODAY));
    }

    @Test
    void longNameCutInTheMachineZoneStillMatches() {
        // a linha do nome tem 30 caracteres: o ultimo nome vem pela metade
        String cut = "MARIA<<APARECIDA<DOS<SANTOS<OL";
        CnhParser.CnhData cnh = CnhParser.parse(ocr("D", cut, mrzDates("900514", "330101")));

        assertTrue(cnh.nameTruncated());
        assertTrue(CnhParser.sameName(cnh.name(), true, "Maria Aparecida dos Santos Oliveira"));
        assertFalse(CnhParser.sameName(cnh.name(), true, "Maria Aparecida dos Santos Pereira"));
    }

    @Test
    void twoReadingsAreMerged() {
        CnhParser.CnhData first = new CnhParser.CnhData(null, "JOAO SILVA", false, LocalDate.of(1990, 5, 14), null, null);
        CnhParser.CnhData second = new CnhParser.CnhData("52998224725", null, false, null, LocalDate.of(2033, 1, 1), "D");

        CnhParser.CnhData merged = first.orElse(second);

        assertTrue(merged.complete());
        assertEquals("JOAO SILVA", merged.name());
    }
}
