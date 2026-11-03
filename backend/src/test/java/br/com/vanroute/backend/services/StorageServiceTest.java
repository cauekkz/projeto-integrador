package br.com.vanroute.backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import br.com.vanroute.backend.repositories.ChatMessageRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(storageService, "endpoint", "http://localhost:9000");
    }

    @Test
    void testInitBucketWhenBucketExists() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        storageService.initBucket();

        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void testGeneratePresignedUrl() throws Exception {
        PresignedPutObjectRequest presignedReq = mock(PresignedPutObjectRequest.class);
        when(presignedReq.url()).thenReturn(new URI("http://localhost:9000/test-bucket/some-key").toURL());
        
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedReq);

        UUID owner = UUID.randomUUID();
        StorageService.PresignedUrlResponse response = storageService.generatePresignedUrl(owner, "doc.pdf", "application/pdf");

        assertNotNull(response);
        assertNotNull(response.uploadUrl());
        assertEquals("http://localhost:9000/test-bucket/some-key", response.uploadUrl());
        assertTrue(response.fileKey().endsWith(".pdf"));
        // o caminho diz quem enviou
        assertTrue(response.fileKey().startsWith("uploads/" + owner + "/"));
        assertTrue(StorageService.isOwnedBy(response.fileKey(), owner));
        assertFalse(StorageService.isOwnedBy(response.fileKey(), UUID.randomUUID()));
    }

    // o id vem do token; o download nao consulta mais o usuario
    private UUID userWithCpf(String ignored) {
        return UUID.randomUUID();
    }

    @Test
    void testDownloadUrlForChatParticipant() throws Exception {
        UUID userId = userWithCpf("111");
        when(chatMessageRepository.existsAttachmentVisibleTo("foto.jpeg", userId)).thenReturn(true);
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("http://localhost:9000/test-bucket/foto.jpeg?X-Amz-Signature=x").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        StorageService.PresignedDownloadResponse response = storageService.generateDownloadUrl("foto.jpeg", userId);

        assertEquals("foto.jpeg", response.fileKey());
        assertTrue(response.downloadUrl().contains("X-Amz-Signature"));
    }

    @Test
    void testDownloadUrlForContractParty() throws Exception {
        UUID userId = userWithCpf("111");
        when(chatMessageRepository.existsAttachmentVisibleTo("contrato.pdf", userId)).thenReturn(false);
        when(documentRepository.existsContractDocumentVisibleTo("contrato.pdf", userId)).thenReturn(true);
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("http://localhost:9000/test-bucket/contrato.pdf").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        assertNotNull(storageService.generateDownloadUrl("contrato.pdf", userId).downloadUrl());
    }

    @Test
    void testDownloadUrlDeniedForOutsider() {
        UUID userId = userWithCpf("222");
        when(chatMessageRepository.existsAttachmentVisibleTo("foto.jpeg", userId)).thenReturn(false);
        when(documentRepository.existsContractDocumentVisibleTo("foto.jpeg", userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> storageService.generateDownloadUrl("foto.jpeg", userId));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    // PDF do contrato: o arquivo enviado e conferido e copiado pra contracts/{id}.pdf (onde ninguem grava)
    private void uploaded(String key, long size, String firstBytes) {
        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder().contentLength(size).build());
        if (firstBytes != null) {
            when(s3Client.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                    .thenReturn(software.amazon.awssdk.core.ResponseBytes.fromByteArray(
                            software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build(),
                            firstBytes.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        }
    }

    @Test
    void contractPdfIsFrozenInAFolderNobodyWrites() {
        UUID contractId = UUID.randomUUID();
        uploaded("uploads/d/x.pdf", 2048, "%PDF-");

        String frozen = storageService.freezeContractPdf("uploads/d/x.pdf", contractId);

        assertEquals("contracts/" + contractId + ".pdf", frozen);
        verify(s3Client).copyObject(org.mockito.ArgumentMatchers.<software.amazon.awssdk.services.s3.model.CopyObjectRequest>argThat(
                copy -> copy.sourceKey().equals("uploads/d/x.pdf") && copy.destinationKey().equals(frozen)));
        verify(s3Client).deleteObject(org.mockito.ArgumentMatchers.<software.amazon.awssdk.services.s3.model.DeleteObjectRequest>argThat(
                delete -> delete.key().equals("uploads/d/x.pdf")));
    }

    @Test
    void contractThatIsNotAPdfIsRefused() {
        uploaded("uploads/d/foto.pdf", 2048, "\u0089PNG");

        assertThrows(br.com.vanroute.backend.exceptions.BadRequestException.class,
                () -> storageService.freezeContractPdf("uploads/d/foto.pdf", UUID.randomUUID()));
        verify(s3Client, never()).copyObject(any(software.amazon.awssdk.services.s3.model.CopyObjectRequest.class));
    }

    @Test
    void fileOverTenMegabytesIsRefused() {
        uploaded("uploads/d/grande.pdf", StorageService.MAX_FILE_BYTES + 1, null);

        assertThrows(br.com.vanroute.backend.exceptions.BadRequestException.class,
                () -> storageService.freezeContractPdf("uploads/d/grande.pdf", UUID.randomUUID()));
        verify(s3Client, never()).copyObject(any(software.amazon.awssdk.services.s3.model.CopyObjectRequest.class));
    }
}
