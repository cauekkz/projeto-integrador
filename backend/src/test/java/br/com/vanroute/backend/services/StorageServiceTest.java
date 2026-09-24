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

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

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

        StorageService.PresignedUrlResponse response = storageService.generatePresignedUrl("doc.pdf", "application/pdf");

        assertNotNull(response);
        assertNotNull(response.uploadUrl());
        assertEquals("http://localhost:9000/test-bucket/some-key", response.uploadUrl());
        assertTrue(response.fileKey().endsWith(".pdf"));
    }
}
