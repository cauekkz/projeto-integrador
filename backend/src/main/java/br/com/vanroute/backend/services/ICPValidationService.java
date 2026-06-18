//INTEIRA GPT
package br.com.vanroute.backend.services;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import br.com.vanroute.backend.dtos.user.IcpExtractedInfo;

import java.io.InputStream;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ICPValidationService {

    private X509Certificate rootCertificate;

    public ICPValidationService() {
        Security.addProvider(new BouncyCastleProvider());
        try {
            ClassPathResource resource = new ClassPathResource("certs/ICP-Brasilv5.crt");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
                    this.rootCertificate = (X509Certificate) cf.generateCertificate(is);
                }
            } else {
                System.out.println("Certificado Raiz ICP-Brasilv5.crt não encontrado!");
            }
        } catch (Exception e) {
            // Pode injetar um Logger se desejar salvar no arquivo de log do servidor, 
            // mas por enquanto foi removido o print do stacktrace para não poluir o stdout.
            throw new RuntimeException("Erro ao carregar o certificado ICP-Brasil", e);
        }
    }

    public void validateDocumentSignature(MultipartFile pdfFile) {
        try {
            byte[] pdfBytes = pdfFile.getBytes();

            int[] bRange = extractByteRange(pdfBytes);
            byte[] signedContent = buildSignedContent(pdfBytes, bRange);

            byte[] signatureBytes = extractSignatureFromPdf(pdfBytes, bRange);

            verifyPdfSignature(signatureBytes, signedContent);

        } catch (Exception e) {
            throw new RuntimeException("Falha ao validar a assinatura digital ICP-Brasil: " + e.getMessage(), e);
        }
    }

    private int[] extractByteRange(byte[] pdfBytes) throws Exception {
        String pdfStr = new String(pdfBytes, "ISO-8859-1");
        Pattern pattern = Pattern.compile("/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*\\]");
        Matcher matcher = pattern.matcher(pdfStr);
        if (!matcher.find()) throw new Exception("ByteRange não encontrado");

        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4))
        };
    }

    private byte[] buildSignedContent(byte[] pdfBytes, int[] bRange) {
        int a = bRange[0], b = bRange[1], c = bRange[2], d = bRange[3];
        byte[] result = new byte[b + d];
        System.arraycopy(pdfBytes, a, result, 0, b);
        System.arraycopy(pdfBytes, c, result, b, d);
        return result;
    }

    private byte[] extractSignatureFromPdf(byte[] pdfBytes, int[] bRange) throws Exception {
        int a = bRange[0], b = bRange[1], c = bRange[2];
        int sigLength = c - (a + b);

        byte[] hexSig = new byte[sigLength];
        System.arraycopy(pdfBytes, a + b, hexSig, 0, sigLength);

        String hexStr = new String(hexSig, "ISO-8859-1").replaceAll("[<>\\x00]", "").trim();
        
        int len = hexStr.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexStr.charAt(i), 16) << 4)
                    + Character.digit(hexStr.charAt(i + 1), 16));
        }
        return data;
    }

    private void verifyPdfSignature(byte[] pkcs7Bytes, byte[] signedContent) throws Exception {
        CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), pkcs7Bytes);
        SignerInformationStore signers = cms.getSignerInfos();
        Collection<SignerInformation> c = signers.getSigners();

        List<X509Certificate> certs = new ArrayList<>();
        JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
        for (X509CertificateHolder holder : cms.getCertificates().getMatches(null)) {
            certs.add(converter.getCertificate(holder));
        }

        if (c.isEmpty()) throw new Exception("Nenhum assinante encontrado no PKCS7");
        SignerInformation signer = c.iterator().next();

        X509Certificate signerCert = null;
        for (X509Certificate cert : certs) {
            if (cert.getSerialNumber().equals(signer.getSID().getSerialNumber())) {
                signerCert = cert;
                break;
            }
        }
        if (signerCert == null) throw new Exception("Certificado do signer não encontrado");


        boolean verifies = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signerCert));
        
        if (!verifies) {
            throw new Exception("Hash do conteúdo não confere / Assinatura falhou");
        }
    }

    public IcpExtractedInfo extractDataFromDocument(MultipartFile pdfFile) {
        try {
            byte[] pdfBytes = pdfFile.getBytes();
            int[] bRange = extractByteRange(pdfBytes);
            byte[] signatureBytes = extractSignatureFromPdf(pdfBytes, bRange);

            CMSSignedData cms = new CMSSignedData(signatureBytes);
            SignerInformationStore signers = cms.getSignerInfos();
            Collection<SignerInformation> c = signers.getSigners();

            List<X509Certificate> certs = new ArrayList<>();
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
            for (X509CertificateHolder holder : cms.getCertificates().getMatches(null)) {
                certs.add(converter.getCertificate(holder));
            }

            SignerInformation signer = c.iterator().next();
            X509Certificate signerCert = null;
            for (X509Certificate cert : certs) {
                if (cert.getSerialNumber().equals(signer.getSID().getSerialNumber())) {
                    signerCert = cert;
                    break;
                }
            }

            String subjectDN = signerCert.getSubjectX500Principal().getName();
            String name = "Nome Nao Encontrado";
            String cpf = "00000000000";
            
            Matcher matcher = Pattern.compile("CN=([^:,]+):([0-9]{11})").matcher(subjectDN);
            if (matcher.find()) {
                name = matcher.group(1).trim();
                cpf = matcher.group(2);
            } else {
                name = subjectDN; 
            }

            return new IcpExtractedInfo(name, cpf);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao extrair dados do usuario no ICP-Brasil: " + e.getMessage(), e);
        }
    }
}
