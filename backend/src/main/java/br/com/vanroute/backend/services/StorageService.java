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
import br.com.vanroute.backend.exceptions.BadRequestException;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import br.com.vanroute.backend.repositories.ChatMessageRepository;
import br.com.vanroute.backend.repositories.DocumentRepository;
import br.com.vanroute.backend.repositories.UserRepository;
import org.springframework.security.access.AccessDeniedException;
//IA FIRST
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
    private final UserRepository userRepository;

    public StorageService(S3Client s3Client, S3Presigner s3Presigner, ChatMessageRepository chatMessageRepository,
                          DocumentRepository documentRepository, UserRepository userRepository) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
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

    public PresignedUrlResponse generatePresignedUrl(String originalFilename, String contentType) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return presignPut(UUID.randomUUID().toString() + ext, contentType);
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

    public boolean exists(String fileKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(fileKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public void delete(String fileKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(fileKey).build());
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
