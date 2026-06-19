package br.com.vanroute.backend.services;
//IA PURA TEM Q ANALIZA

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentOcrExtractionService {

    private static final Pattern CPF_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{3})[.\\s-]?(\\d{3})[.\\s-]?(\\d{3})[.\\s-]?(\\d{2})(?!\\d)"
    );

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

    public String extractFromCnh(MultipartFile pdfFile) {
        try {

            BufferedImage image = renderPdfToImage(pdfFile.getBytes());

            BufferedImage processed = preprocess(image);

            String text = tesseract.doOCR(processed);

            String cpf = extractCpf(text);

            if (cpf == null) {
                throw new RuntimeException("CPF não encontrado na CNH. Texto OCR: " + text);
            }

            return cpf;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar CNH", e);
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

    private BufferedImage renderPdfToImage(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            PDFRenderer renderer = new PDFRenderer(document);

            return renderer.renderImageWithDPI(0, 450);
        }
    }

    private String extractCpf(String text) {

        if (text == null) return null;

        Matcher matcher = CPF_PATTERN.matcher(text);

        while (matcher.find()) {

            String cpf = matcher.group(1)
                    + matcher.group(2)
                    + matcher.group(3)
                    + matcher.group(4);

            cpf = cpf.replaceAll("\\D", "");

            if (isValidCpf(cpf)) {
                return cpf;
            }
        }

        return null;
    }

    private boolean isValidCpf(String cpf) {

        if (cpf == null || cpf.length() != 11) return false;

        if (cpf.chars().distinct().count() == 1) return false;

        return true;
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