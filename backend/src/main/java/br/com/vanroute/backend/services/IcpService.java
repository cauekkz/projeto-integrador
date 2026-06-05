// PURA IA SO JOGUEI O CODIGO QUE TINHA FEITO EM PYTHON E FALEI PRA TRANSFORMAR EM JAVA AINDA TEM QUE TESTAR ALGUNS CASOS, MAS ATÉ ENTÃO FUNCIONAL

package br.com.vanroute.backend.services;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.vanroute.backend.dtos.user.IcpExtractedInfo;

@Service
public class IcpService {

    private static final Pattern BYTE_RANGE_PATTERN = Pattern.compile(
            "/ByteRange\\s*\\[\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*\\]"
    );
    private static final Pattern FORMATTED_CPF_PATTERN = Pattern.compile(
            "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$"
    );

    private final X509Certificate rootCertificate;
    private final X500Name rootSubject;
    private final JcaX509CertificateConverter certificateConverter;

    public IcpService() {
        Security.addProvider(new BouncyCastleProvider());
        this.certificateConverter = new JcaX509CertificateConverter().setProvider("BC");
        this.rootCertificate = loadRootCertificate();
        this.rootSubject = subjectName(rootCertificate);
    }

    public void validateDocumentSignature(MultipartFile pdfFile) {
        try {
            validateDocumentSignature(pdfFile.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao validar a assinatura digital ICP-Brasil: " + e.getMessage(), e);
        }
    }

    public IcpExtractedInfo extractDataFromDocument(MultipartFile pdfFile) {
        try {
            return extractFromPdfText(pdfFile.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao extrair dados do documento ICP-Brasil: " + e.getMessage(), e);
        }
    }

    private void validateDocumentSignature(byte[] pdfBytes) throws Exception {
        int[] byteRange = extractByteRange(pdfBytes);
        byte[] signedContent = buildSignedContent(pdfBytes, byteRange);
        byte[] pkcs7Bytes = extractPkcs7FromPdf(pdfBytes, byteRange);

        CMSSignedData cms = new CMSSignedData(pkcs7Bytes);
        SignerInformation signer = getSigner(cms);
        List<X509Certificate> embeddedCerts = extractCertificates(cms);
        X509Certificate signerCert = findSignerCert(embeddedCerts, signer);

        Date signingDate = extractSigningDate(signer);
        List<X509Certificate> chain = buildChain(signerCert, new ArrayList<>(embeddedCerts));
        validateChain(chain, signingDate);
        verifyPdfIntegrity(signer, signerCert, signedContent);
    }

    private IcpExtractedInfo extractFromPdfText(byte[] pdfBytes) throws Exception {
        List<String> lines = extractTextLines(pdfBytes);

        int cpfLineIndex = -1;
        String cpf = null;
        for (int i = 0; i < lines.size(); i++) {
            String normalized = normalizeCpf(lines.get(i));
            if (normalized != null && isFormattedCpfLine(lines.get(i))) {
                cpf = normalized;
                cpfLineIndex = i;
                break;
            }
        }

        if (cpf == null) {
            for (int i = 0; i < lines.size(); i++) {
                String normalized = normalizeCpf(lines.get(i));
                if (normalized != null) {
                    cpf = normalized;
                    cpfLineIndex = i;
                    break;
                }
            }
        }

        if (cpf == null) {
            throw new Exception(
                    "CPF não encontrado no texto do PDF. Alguns PDFs de CNH exportados só trazem foto/dados "
                            + "como imagem, sem camada de texto; use CRLV-e ou exporte novamente pelo app oficial."
            );
        }

        String name = findNameNearCpf(lines, cpfLineIndex);
        if (name == null) {
            throw new Exception("Nome não encontrado no texto do PDF");
        }

        return new IcpExtractedInfo(name, cpf);
    }

    private List<String> extractTextLines(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            return Arrays.stream(text.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }

    private boolean isFormattedCpfLine(String line) {
        return FORMATTED_CPF_PATTERN.matcher(line.trim()).matches();
    }

    private String findNameNearCpf(List<String> lines, int cpfLineIndex) {
        for (int i = cpfLineIndex - 1; i >= Math.max(0, cpfLineIndex - 5); i--) {
            if (looksLikePersonName(lines.get(i))) {
                return lines.get(i);
            }
        }
        for (int i = cpfLineIndex + 1; i < Math.min(lines.size(), cpfLineIndex + 3); i++) {
            if (looksLikePersonName(lines.get(i))) {
                return lines.get(i);
            }
        }
        return null;
    }

    private boolean looksLikePersonName(String line) {
        if (line == null || line.length() < 4 || line.length() > 120) {
            return false;
        }
        String upper = line.toUpperCase();
        if (upper.equals("NOME") || upper.startsWith("CPF") || upper.contains("DETRAN")
                || upper.contains("SENATRAN") || upper.contains("REPÚBLICA") || upper.contains("QR")) {
            return false;
        }
        if (line.matches(".*\\d.*")) {
            return false;
        }
        return line.matches("^[\\p{L}][\\p{L}\\s'.-]+$");
    }

    private String normalizeCpf(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return digits;
        }
        return null;
    }

    private Date extractSigningDate(SignerInformation signer) {
        try {
            AttributeTable signedAttributes = signer.getSignedAttributes();
            if (signedAttributes == null) {
                return new Date();
            }
            Attribute signingTime = signedAttributes.get(CMSAttributes.signingTime);
            if (signingTime == null) {
                return new Date();
            }
            ASN1UTCTime utcTime = ASN1UTCTime.getInstance(signingTime.getAttrValues().getObjectAt(0));
            return utcTime.getDate();
        } catch (Exception e) {
            return new Date();
        }
    }

    private X509Certificate loadRootCertificate() {
        try {
            ClassPathResource resource = new ClassPathResource("certs/ICP-Brasil/ICP-Brasilv5.crt");
            if (!resource.exists()) {
                throw new IllegalStateException("Certificado raiz ICP-Brasilv5.crt não encontrado");
            }
            try (InputStream is = resource.getInputStream()) {
                CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
                return (X509Certificate) factory.generateCertificate(is);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar o certificado ICP-Brasil", e);
        }
    }

    private X500Name subjectName(X509Certificate cert) {
        return X500Name.getInstance(cert.getSubjectX500Principal().getEncoded());
    }

    private X500Name issuerName(X509Certificate cert) {
        return X500Name.getInstance(cert.getIssuerX500Principal().getEncoded());
    }

    private int[] extractByteRange(byte[] pdfBytes) throws Exception {
        Matcher matcher = BYTE_RANGE_PATTERN.matcher(new String(pdfBytes, "ISO-8859-1"));
        if (!matcher.find()) {
            throw new Exception("ByteRange não encontrado");
        }
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4))
        };
    }

    private byte[] buildSignedContent(byte[] pdfBytes, int[] byteRange) {
        int a = byteRange[0];
        int b = byteRange[1];
        int c = byteRange[2];
        int d = byteRange[3];
        byte[] result = new byte[b + d];
        System.arraycopy(pdfBytes, a, result, 0, b);
        System.arraycopy(pdfBytes, c, result, b, d);
        return result;
    }

    private byte[] extractPkcs7FromPdf(byte[] pdfBytes, int[] byteRange) throws Exception {
        int signatureStart = byteRange[0] + byteRange[1];
        int signatureLength = byteRange[2] - signatureStart;

        byte[] hexSignature = new byte[signatureLength];
        System.arraycopy(pdfBytes, signatureStart, hexSignature, 0, signatureLength);

        String hex = new String(hexSignature, "ISO-8859-1").replaceAll("[<>\\x00]", "").trim();
        byte[] pkcs7 = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            pkcs7[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return pkcs7;
    }

    private SignerInformation getSigner(CMSSignedData cms) throws Exception {
        SignerInformationStore signers = cms.getSignerInfos();
        Collection<SignerInformation> signerCollection = signers.getSigners();
        if (signerCollection.isEmpty()) {
            throw new Exception("Nenhum assinante encontrado no PKCS7");
        }
        return signerCollection.iterator().next();
    }

    private List<X509Certificate> extractCertificates(CMSSignedData cms) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        for (X509CertificateHolder holder : cms.getCertificates().getMatches(null)) {
            certs.add(certificateConverter.getCertificate(holder));
        }
        return certs;
    }

    private X509Certificate findSignerCert(List<X509Certificate> certs, SignerInformation signer) throws Exception {
        SignerId sid = signer.getSID();

        for (X509Certificate cert : certs) {
            if (!cert.getSerialNumber().equals(sid.getSerialNumber())) {
                continue;
            }
            if (sid.getIssuer() != null && !issuerName(cert).equals(sid.getIssuer())) {
                continue;
            }
            return cert;
        }

        byte[] subjectKeyId = sid.getSubjectKeyIdentifier();
        if (subjectKeyId != null) {
            for (X509Certificate cert : certs) {
                byte[] extensionValue = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
                if (extensionValue == null) {
                    continue;
                }
                byte[] ski = ASN1OctetString.getInstance(extensionValue).getOctets();
                if (Arrays.equals(ski, subjectKeyId)) {
                    return cert;
                }
            }
        }

        throw new Exception("Certificado do assinante não encontrado");
    }

    private List<X509Certificate> buildChain(X509Certificate endCert, List<X509Certificate> known) throws Exception {
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(endCert);
        X509Certificate current = endCert;

        while (true) {
            if (issuerName(current).equals(rootSubject)) {
                chain.add(rootCertificate);
                return chain;
            }

            X509Certificate issuer = findIssuer(current, known);
            if (issuer == null) {
                List<X509Certificate> downloaded = new ArrayList<>();
                for (String url : getCaIssuerUrls(current)) {
                    downloaded.addAll(downloadCertificates(url));
                }
                if (downloaded.isEmpty()) {
                    throw new Exception("Cadeia de certificados incompleta");
                }
                known.addAll(downloaded);
                issuer = findIssuer(current, known);
                if (issuer == null) {
                    throw new Exception("Cadeia de certificados incompleta");
                }
            }

            chain.add(issuer);
            current = issuer;
        }
    }

    private X509Certificate findIssuer(X509Certificate cert, List<X509Certificate> known) {
        X500Name expectedIssuer = issuerName(cert);
        for (X509Certificate candidate : known) {
            if (expectedIssuer.equals(subjectName(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private void validateChain(List<X509Certificate> chain, Date validationDate) throws Exception {
        for (int i = 0; i < chain.size() - 1; i++) {
            X509Certificate cert = chain.get(i);
            X509Certificate issuer = chain.get(i + 1);
            cert.checkValidity(validationDate);
            cert.verify(issuer.getPublicKey());
        }

        X509Certificate root = chain.get(chain.size() - 1);
        root.checkValidity(validationDate);
        root.verify(root.getPublicKey());
    }

    private void verifyPdfIntegrity(SignerInformation signer, X509Certificate signerCert, byte[] signedContent)
            throws Exception {
        AttributeTable signedAttributes = signer.getSignedAttributes();
        if (signedAttributes == null) {
            throw new Exception("Atributos assinados ausentes");
        }

        ASN1ObjectIdentifier digestOid = signer.getDigestAlgorithmID().getAlgorithm();
        MessageDigest messageDigest = createMessageDigest(digestOid);
        byte[] contentDigest = messageDigest.digest(signedContent);

        Attribute messageDigestAttr = signedAttributes.get(CMSAttributes.messageDigest);
        if (messageDigestAttr == null) {
            throw new Exception("Message digest ausente nos atributos assinados");
        }

        byte[] expectedDigest = ((ASN1OctetString) messageDigestAttr.getAttrValues().getObjectAt(0)).getOctets();
        if (!Arrays.equals(contentDigest, expectedDigest)) {
            throw new Exception("PDF alterado ou assinatura inválida");
        }

        byte[] signedAttributesDer = encodeSignedAttributesForPades(signer);
        String signatureAlgorithm = signatureAlgorithmForDigest(digestOid);
        Signature signature = Signature.getInstance(signatureAlgorithm, "BC");
        signature.initVerify(signerCert.getPublicKey());
        signature.update(signedAttributesDer);
        if (!signature.verify(signer.getSignature())) {
            throw new Exception("PDF alterado ou assinatura inválida");
        }
    }

    private byte[] encodeSignedAttributesForPades(SignerInformation signer) throws Exception {
        byte[] signedAttributesDer = signer.getEncodedSignedAttributes();
        if (signedAttributesDer.length > 0 && (signedAttributesDer[0] & 0xFF) == 0xA0) {
            signedAttributesDer[0] = 0x31;
        }
        return signedAttributesDer;
    }

    private MessageDigest createMessageDigest(ASN1ObjectIdentifier digestOid) throws Exception {
        if (NISTObjectIdentifiers.id_sha256.equals(digestOid)) {
            return MessageDigest.getInstance("SHA-256");
        }
        if (NISTObjectIdentifiers.id_sha384.equals(digestOid)) {
            return MessageDigest.getInstance("SHA-384");
        }
        if (NISTObjectIdentifiers.id_sha512.equals(digestOid)) {
            return MessageDigest.getInstance("SHA-512");
        }
        if (OIWObjectIdentifiers.idSHA1.equals(digestOid)) {
            return MessageDigest.getInstance("SHA-1");
        }
        throw new Exception("Algoritmo de digest não suportado: " + digestOid.getId());
    }

    private String signatureAlgorithmForDigest(ASN1ObjectIdentifier digestOid) throws Exception {
        if (NISTObjectIdentifiers.id_sha256.equals(digestOid)) {
            return "SHA256withRSA";
        }
        if (NISTObjectIdentifiers.id_sha384.equals(digestOid)) {
            return "SHA384withRSA";
        }
        if (NISTObjectIdentifiers.id_sha512.equals(digestOid)) {
            return "SHA512withRSA";
        }
        if (OIWObjectIdentifiers.idSHA1.equals(digestOid)) {
            return "SHA1withRSA";
        }
        throw new Exception("Algoritmo de assinatura não suportado: " + digestOid.getId());
    }

    private List<String> getCaIssuerUrls(X509Certificate cert) {
        List<String> urls = new ArrayList<>();
        try {
            byte[] extensionValue = cert.getExtensionValue(Extension.authorityInfoAccess.getId());
            if (extensionValue == null) {
                return urls;
            }

            byte[] octets = ASN1OctetString.getInstance(extensionValue).getOctets();
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(octets);

            for (AccessDescription access : aia.getAccessDescriptions()) {
                if (!X509ObjectIdentifiers.id_ad_caIssuers.equals(access.getAccessMethod())) {
                    continue;
                }
                String url = access.getAccessLocation().getName().toString();
                if (url.startsWith("http")) {
                    urls.add(url);
                }
            }
        } catch (Exception ignored) {
            // sem AIA utilizável
        }
        return urls;
    }

    private List<X509Certificate> downloadCertificates(String url) {
        List<X509Certificate> certs = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return certs;
            }
            certs.addAll(parsePkcs7OrDerCertificates(response.body()));
        } catch (Exception ignored) {
            // falha no download não interrompe outras URLs
        }
        return certs;
    }

    private List<X509Certificate> parsePkcs7OrDerCertificates(byte[] data) {
        List<X509Certificate> certs = new ArrayList<>();
        try {
            CMSSignedData pkcs7 = new CMSSignedData(data);
            for (X509CertificateHolder holder : pkcs7.getCertificates().getMatches(null)) {
                certs.add(certificateConverter.getCertificate(holder));
            }
            if (!certs.isEmpty()) {
                return certs;
            }
        } catch (Exception ignored) {
            // tenta outros formatos
        }

        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
            for (Certificate certificate : factory.generateCertificates(new ByteArrayInputStream(data))) {
                certs.add((X509Certificate) certificate);
            }
        } catch (Exception ignored) {
            // formato não reconhecido
        }
        return certs;
    }
}
