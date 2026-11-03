package br.com.vanroute.backend.services;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Object;
import br.com.vanroute.backend.exceptions.BadRequestException;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import br.com.vanroute.backend.repositories.ChatMessageRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import org.springframework.security.access.AccessDeniedException;
import java.time.Duration;
import java.util.UUID;

@Service
public class StorageService {
    
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${spring.cloud.aws.s3.endpoint}")
    private String endpoint;

    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;

    public StorageService(S3Client s3Client, S3Presigner s3Presigner, ChatMessageRepository chatMessageRepository,
                          DocumentRepository documentRepository) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
    }

    @PostConstruct
    public void initBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("Bucket {} already exists.", bucketName);
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                log.info("Bucket {} not found or requires creation. Creating it...", bucketName);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Bucket {} created successfully.", bucketName);
            } else {
                log.error("Failed to check or create bucket", e);
            }
        }
    }

    public static final String UPLOAD_PREFIX = "uploads/";

    // o arquivo vai pra pasta de quem enviou (uploads/{id}/...): o caminho ja diz o dono,
    // sem tabela. Anexar no chat ou usar como PDF do contrato so com arquivo da propria pasta
    public PresignedUrlResponse generatePresignedUrl(UUID userId, String originalFilename, String contentType) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return presignPut(UPLOAD_PREFIX + userId + "/" + UUID.randomUUID() + ext, contentType);
    }

    public static boolean isOwnedBy(String fileKey, UUID userId) {
        return fileKey != null && fileKey.startsWith(UPLOAD_PREFIX + userId + "/");
    }

    public static final String PROFILE_PREFIX = "profile/";

    // foto de perfil vai pra uma pasta propria do usuario; so essa pasta e publica pra usuarios logados
    public PresignedUrlResponse generateProfilePhotoUploadUrl(UUID userId, String originalFilename, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("A foto de perfil precisa ser uma imagem");
        }
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return presignPut(PROFILE_PREFIX + userId + "/" + UUID.randomUUID() + ext, contentType);
    }

    // limite de tamanho de anexo do chat e do PDF do contrato. A URL de upload nao consegue limitar
    // (o tamanho nao e assinado), entao o arquivo e conferido quando e usado
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    // PDF do contrato: depois de anexado a proposta, ele e copiado pra ca. Nenhuma URL de upload grava nessa pasta,
    // entao o arquivo que as duas partes assinaram nao muda mais (a URL de upload original vale 15 minutos e
    // deixaria o motorista trocar o PDF nesse tempo)
    public static final String CONTRACTS_PREFIX = "contracts/";

    public boolean exists(String fileKey) {
        return head(fileKey) != null;
    }

    private HeadObjectResponse head(String fileKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(fileKey).build());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    // arquivo enviado pela pessoa, existente e dentro do limite (anexo do chat)
    public void checkUploaded(String fileKey) {
        HeadObjectResponse head = head(fileKey);
        if (head == null) {
            throw new BadRequestException("Arquivo não encontrado. Envie de novo");
        }
        if (head.contentLength() > MAX_FILE_BYTES) {
            throw new BadRequestException("O arquivo pode ter no máximo " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB");
        }
    }

    // o front ja enviou o PDF pro MinIO (presigned-url + PUT) antes de mandar a proposta. Aqui o backend confere,
    // sem baixar o arquivo, que ele existe, tem ate 10 MB e e um PDF de verdade (os 5 primeiros bytes sao %PDF-),
    // e o MinIO copia ele pra contracts/{contractId}.pdf. O enviado so e apagado depois que a proposta e gravada:
    // se ela falhar, o arquivo continua e o front pode tentar de novo com o mesmo fileKey
    public String freezeContractPdf(String fileKey, UUID contractId) {
        checkUploaded(fileKey);
        byte[] start;
        try {
            start = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(fileKey).range("bytes=0-4").build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BadRequestException("O PDF do contrato não foi encontrado. Envie o arquivo de novo");
        }
        if (!new String(start, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-")) {
            throw new BadRequestException("O contrato precisa ser um arquivo PDF");
        }
        String frozenKey = CONTRACTS_PREFIX + contractId + ".pdf";
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName).sourceKey(fileKey)
                .destinationBucket(bucketName).destinationKey(frozenKey)
                .contentType("application/pdf").metadataDirective(MetadataDirective.REPLACE)
                .build());
        afterCommit(() -> delete(fileKey));
        return frozenKey;
    }

    private static void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    public void delete(String fileKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(fileKey).build());
    }

    // apaga tudo de uma pasta (excluir conta: uploads/{id}/ e profile/{id}/)
    public void deleteFolder(String prefix) {
        String token = null;
        do {
            var page = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix)
                    .continuationToken(token).build());
            for (S3Object object : page.contents()) {
                delete(object.key());
            }
            token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (token != null);
    }

    private PresignedUrlResponse presignPut(String fileKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedPutObjectRequest.url().toString();

        return new PresignedUrlResponse(presignedUrl, fileKey); // Returns the presigned upload URL and the permanent download URL
    }
    
    // o bucket continua privado: so gera link temporario de leitura para quem participa do chat
    // onde o arquivo foi enviado ou e parte do contrato do documento
    public PresignedDownloadResponse generateDownloadUrl(String fileKey, UUID userId) {
        // foto de perfil: qualquer usuario logado ve
        boolean allowed = fileKey.startsWith(PROFILE_PREFIX)
                || isOwnedBy(fileKey, userId)
                || chatMessageRepository.existsAttachmentVisibleTo(fileKey, userId)
                || documentRepository.existsContractDocumentVisibleTo(fileKey, userId);
        if (!allowed) {
            throw new AccessDeniedException("Sem acesso a este arquivo");
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(fileKey).build())
                .build();

        return new PresignedDownloadResponse(s3Presigner.presignGetObject(presignRequest).url().toString(), fileKey);
    }

    public record PresignedDownloadResponse(String downloadUrl, String fileKey) {}

    //IA burro nao separou por pasta, mas tbm é tao pequeno deixo o bicho aqui    
    public record PresignedUrlResponse(String uploadUrl, String fileKey) {}
}
