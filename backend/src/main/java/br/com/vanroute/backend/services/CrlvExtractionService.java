package br.com.vanroute.backend.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// le os dados do veiculo do CRLV digital pelo texto do PDF (sem OCR):
// cada valor fica logo abaixo do rotulo, na mesma coluna
@Service
public class CrlvExtractionService {

    // distancia maxima (pt) entre o rotulo e o valor embaixo dele
    private static final float MAX_VERTICAL_GAP = 40;
    private static final float MAX_COLUMN_OFFSET = 8;
    // espaco horizontal que separa duas colunas na mesma linha
    private static final float COLUMN_GAP = 12;

    public record TextSegment(String text, float x, float y) {
    }

    public record CrlvData(
            String plate,
            String renavam,
            String chassis,
            String model,
            Integer manufactureYear,
            Integer modelYear,
            String color,
            String fuel,
            String category,
            Integer licensingYear,
            Integer passengers
    ) {
    }

    public CrlvData extract(MultipartFile pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.getBytes())) {
            return parse(readSegments(document));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível ler o CRLV");
        }
    }

    public static CrlvData parse(List<TextSegment> segments) {
        String plate = normalize(valueBelow(segments, "PLACA"));
        String renavam = digits(valueBelow(segments, "CÓDIGO RENAVAM"));
        if (plate == null || renavam == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível ler placa e RENAVAM do CRLV");
        }
        return new CrlvData(
                plate,
                renavam,
                normalize(valueBelow(segments, "CHASSI")),
                valueBelow(segments, "MARCA / MODELO / VERSÃO"),
                number(valueBelow(segments, "ANO FABRICAÇÃO")),
                number(valueBelow(segments, "ANO MODELO")),
                valueBelow(segments, "COR PREDOMINANTE"),
                valueBelow(segments, "COMBUSTÍVEL"),
                valueBelow(segments, "CATEGORIA"),
                number(valueBelow(segments, "EXERCÍCIO")),
                // lotacao vem como "15P" (passageiros)
                number(valueBelow(segments, "LOTAÇÃO"))
        );
    }

    private static String valueBelow(List<TextSegment> segments, String label) {
        TextSegment labelSegment = segments.stream()
                .filter(segment -> segment.text().equals(label))
                .findFirst()
                .orElse(null);
        if (labelSegment == null) {
            return null;
        }
        return segments.stream()
                .filter(segment -> segment.y() > labelSegment.y() + 1
                        && segment.y() < labelSegment.y() + MAX_VERTICAL_GAP
                        && Math.abs(segment.x() - labelSegment.x()) < MAX_COLUMN_OFFSET)
                .min(Comparator.comparing(TextSegment::y))
                .map(TextSegment::text)
                .filter(text -> !text.isBlank() && !text.startsWith("*"))
                .orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private static String digits(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static Integer number(String value) {
        String digits = digits(value);
        return digits == null ? null : Integer.valueOf(digits);
    }

    // quebra cada linha do PDF em blocos por coluna, guardando a posicao de cada bloco
    private List<TextSegment> readSegments(PDDocument document) throws IOException {
        List<TextSegment> segments = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                StringBuilder current = new StringBuilder();
                float startX = -1;
                float lastEnd = -1;
                float y = 0;
                for (TextPosition position : positions) {
                    if (startX >= 0 && position.getXDirAdj() - lastEnd > COLUMN_GAP) {
                        segments.add(new TextSegment(current.toString().trim(), startX, y));
                        current.setLength(0);
                        startX = -1;
                    }
                    if (startX < 0) {
                        startX = position.getXDirAdj();
                        y = position.getYDirAdj();
                    }
                    current.append(position.getUnicode());
                    lastEnd = position.getXDirAdj() + position.getWidthDirAdj();
                }
                if (!current.isEmpty()) {
                    segments.add(new TextSegment(current.toString().trim(), startX, y));
                }
            }
        };
        stripper.setSortByPosition(true);
        stripper.getText(document);
        return segments;
    }
}
