package br.com.vanroute.backend.services;

import br.com.vanroute.backend.exceptions.InvalidDocumentException;

import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class DocumentOcrExtractionService {


    private Tesseract tesseract;

    @PostConstruct
    void init() {
        try {
            Path tessDataPath = resolveTessDataPath();

            tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath.toString());
            tesseract.setLanguage("por");

            tesseract.setPageSegMode(6);
            tesseract.setOcrEngineMode(1);

        } catch (IOException e) {
            throw new IllegalStateException("Erro ao inicializar OCR");
        }
    }

    // leituras do OCR, da mais confiavel pra menos (resolucao, modo de pagina): o OCR erra campos diferentes em
    // cada uma, entao junta ate ter todos
    private static final int[][] CNH_PASSES = {{450, 6}, {300, 6}, {450, 3}, {300, 3}};

    // le a CNH (OCR da imagem do PDF): CPF, nome, nascimento, validade e categoria. Quem confere as regras e o
    // CnhParser.check. O texto lido tem dado pessoal: nao vai na resposta nem no log. synchronized: o Tesseract
    // e um so e nao e thread-safe (e cadastro de motorista e raro)
    public synchronized CnhParser.CnhData extractFromCnh(MultipartFile pdfFile) {
        try {
            byte[] bytes = pdfFile.getBytes();
            CnhParser.CnhData data = CnhParser.parse(null);
            for (int[] pass : CNH_PASSES) {
                tesseract.setPageSegMode(pass[1]);
                data = data.orElse(CnhParser.parse(tesseract.doOCR(preprocess(renderPdfToImage(bytes, pass[0])))));
                if (data.complete()) {
                    break;
                }
            }
            return data;
        } catch (Exception e) {
            throw new InvalidDocumentException("Não foi possível ler a CNH. Envie o PDF original da CNH digital");
        }
    }

    private BufferedImage preprocess(BufferedImage image) {

        BufferedImage gray = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics g = gray.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return gray;
    }

    private BufferedImage renderPdfToImage(byte[] pdfBytes, int dpi) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            PDFRenderer renderer = new PDFRenderer(document);

            return renderer.renderImageWithDPI(0, dpi);
        }
    }

    private Path resolveTessDataPath() throws IOException {

        for (String systemPath : new String[]{
                "/usr/share/tesseract/tessdata",
                "/usr/share/tessdata",
                "/usr/local/share/tessdata"
        }) {

            Path path = Path.of(systemPath);

            if (Files.exists(path.resolve("por.traineddata"))) {
                return path;
            }
        }

        Path tempDir = Files.createTempDirectory("tessdata");

        copyClasspathResource(
                "tessdata/por.traineddata",
                tempDir.resolve("por.traineddata")
        );

        return tempDir;
    }

    private void copyClasspathResource(
            String classpathLocation,
            Path target
    ) throws IOException {

        ClassPathResource resource = new ClassPathResource(classpathLocation);

        try (InputStream inputStream = resource.getInputStream()) {

            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}