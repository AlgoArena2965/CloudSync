package com.cloudsync.s3;

import com.cloudsync.config.AwsS3Config;
import com.cloudsync.config.CloudSyncProperties;
import com.cloudsync.resilience.ResilienceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final AwsS3Config s3Config;
    private final CloudSyncProperties properties;
    private final ResilienceService resilienceService;

    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        this.presigner = S3Presigner.builder()
                .region(Region.of(s3Config.getRegion() != null ? s3Config.getRegion() : "us-east-1"))
                .build();
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                    .bucket(s3Config.getBucket())
                    .build();
            s3Client.headBucket(headRequest);
            log.info("S3 bucket '{}' exists and is accessible", s3Config.getBucket());
        } catch (NoSuchBucketException e) {
            log.info("S3 bucket '{}' does not exist, creating...", s3Config.getBucket());
            createBucket();
        } catch (Exception e) {
            log.warn("Could not verify bucket existence: {}", e.getMessage());
        }
    }

    private void createBucket() {
        try {
            CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(s3Config.getBucket())
                    .build();
            s3Client.createBucket(request);
            log.info("S3 bucket '{}' created successfully", s3Config.getBucket());
        } catch (Exception e) {
            log.error("Failed to create S3 bucket: {}", e.getMessage());
        }
    }

    /**
     * Initiate a multipart upload for resumable uploads.
     */
    public CreateMultipartUploadResponse initiateMultipartUpload(String s3Key, String contentType) {
        return resilienceService.executeWithResilience(() -> {
            log.info("Initiating multipart upload for key: {}", s3Key);

            CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            return s3Client.createMultipartUpload(request);
        });
    }

    /**
     * Upload a single chunk/part of a multipart upload.
     */
    public UploadPartResponse uploadPart(String s3Key, String uploadId, int partNumber, byte[] data) {
        return resilienceService.executeWithResilience(() -> {
            log.debug("Uploading part #{} for uploadId: {}", partNumber, uploadId);

            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength((long) data.length)
                    .build();

            return s3Client.uploadPart(request, RequestBody.fromBytes(data));
        });
    }

    /**
     * Upload a single chunk from an input stream.
     */
    public UploadPartResponse uploadPart(String s3Key, String uploadId, int partNumber, InputStream inputStream, long contentLength) {
        return resilienceService.executeWithResilience(() -> {
            log.debug("Uploading part #{} from stream for uploadId: {}", partNumber, uploadId);

            UploadPartRequest request = UploadPartRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(contentLength)
                    .build();

            return s3Client.uploadPart(request, RequestBody.fromInputStream(inputStream, contentLength));
        });
    }

    /**
     * List all uploaded parts for a multipart upload.
     */
    public ListPartsResponse listParts(String s3Key, String uploadId) {
        return resilienceService.executeWithResilience(() -> {
            ListPartsRequest request = ListPartsRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .uploadId(uploadId)
                    .build();
            return s3Client.listParts(request);
        });
    }

    /**
     * Complete a multipart upload by assembling all parts.
     */
    public CompleteMultipartUploadResponse completeMultipartUpload(String s3Key, String uploadId, List<CompletedPart> parts) {
        return resilienceService.executeWithResilience(() -> {
            log.info("Completing multipart upload for key: {} with {} parts", s3Key, parts.size());

            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(parts)
                    .build();

            CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();

            return s3Client.completeMultipartUpload(request);
        });
    }

    /**
     * Abort a multipart upload (cleanup on failure).
     */
    public void abortMultipartUpload(String s3Key, String uploadId) {
        try {
            resilienceService.executeWithResilience(() -> {
                log.warn("Aborting multipart upload for key: {}, uploadId: {}", s3Key, uploadId);

                AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(s3Key)
                        .uploadId(uploadId)
                        .build();

                s3Client.abortMultipartUpload(request);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to abort multipart upload: {}", e.getMessage());
        }
    }

    /**
     * Simple file upload (for small files).
     */
    public PutObjectResponse uploadFile(String s3Key, MultipartFile file) throws IOException {
        return uploadFile(s3Key, file.getInputStream(), file.getSize(), file.getContentType());
    }

    /**
     * Upload file from input stream.
     */
    public PutObjectResponse uploadFile(String s3Key, InputStream inputStream, long size, String contentType) {
        return resilienceService.executeWithResilience(() -> {
            log.info("Uploading file to S3 key: {}, size: {} bytes", s3Key, size);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build();

            return s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
        });
    }

    /**
     * Upload bytes directly.
     */
    public PutObjectResponse uploadBytes(String s3Key, byte[] data, String contentType) {
        return resilienceService.executeWithResilience(() -> {
            log.info("Uploading bytes to S3 key: {}, size: {} bytes", s3Key, data.length);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();

            return s3Client.putObject(request, RequestBody.fromBytes(data));
        });
    }

    /**
     * Download file as input stream.
     */
    public ResponseInputStream<GetObjectResponse> downloadFile(String s3Key) {
        return resilienceService.executeWithResilience(() -> {
            log.info("Downloading file from S3 key: {}", s3Key);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .build();

            return s3Client.getObject(request);
        });
    }

    /**
     * Download a range of bytes from a file.
     */
    public ResponseInputStream<GetObjectResponse> downloadFileRange(String s3Key, long start, long end) {
        return resilienceService.executeWithResilience(() -> {
            log.debug("Downloading range [{}-{}] from S3 key: {}", start, end, s3Key);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .range(String.format("bytes=%d-%d", start, end))
                    .build();

            return s3Client.getObject(request);
        });
    }

    /**
     * Delete a file from S3.
     */
    public void deleteFile(String s3Key) {
        resilienceService.executeWithResilience(() -> {
            log.info("Deleting file from S3 key: {}", s3Key);

            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(request);
            return null;
        });
    }

    /**
     * Check if a file exists in S3.
     */
    public boolean fileExists(String s3Key) {
        return resilienceService.executeWithResilience(() -> {
            try {
                HeadObjectRequest request = HeadObjectRequest.builder()
                        .bucket(s3Config.getBucket())
                        .key(s3Key)
                        .build();
                s3Client.headObject(request);
                return true;
            } catch (NoSuchKeyException e) {
                return false;
            }
        });
    }

    /**
     * Get file metadata without downloading.
     */
    public HeadObjectResponse getFileMetadata(String s3Key) {
        return resilienceService.executeWithResilience(() -> {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(s3Config.getBucket())
                    .key(s3Key)
                    .build();
            return s3Client.headObject(request);
        });
    }

    /**
     * Generate a pre-signed URL for direct upload (browser/client).
     */
    public String generatePresignedUploadUrl(String s3Key, Duration expiry) {
        return resilienceService.executeWithResilience(() -> {
            log.debug("Generating pre-signed upload URL for key: {}", s3Key);

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(s3Config.getBucket())
                            .key(s3Key)
                            .build())
                    .build();

            return presigner.presignPutObject(presignRequest).url().toString();
        });
    }

    /**
     * Generate a pre-signed URL for download.
     */
    public String generatePresignedDownloadUrl(String s3Key, Duration expiry) {
        return resilienceService.executeWithResilience(() -> {
            log.debug("Generating pre-signed download URL for key: {}", s3Key);

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(s3Config.getBucket())
                            .key(s3Key)
                            .build())
                    .build();

            return presigner.presignGetObject(presignRequest).url().toString();
        });
    }

    /**
     * Copy a file within S3 (for move/rename operations).
     */
    public CopyObjectResponse copyFile(String sourceKey, String destKey) {
        return resilienceService.executeWithResilience(() -> {
            log.info("Copying S3 file from {} to {}", sourceKey, destKey);

            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(s3Config.getBucket())
                    .sourceKey(sourceKey)
                    .destinationBucket(s3Config.getBucket())
                    .destinationKey(destKey)
                    .build();

            return s3Client.copyObject(request);
        });
    }

    /**
     * Get the bucket name.
     */
    public String getBucketName() {
        return s3Config.getBucket();
    }

    public ResilienceService.CircuitBreakerMetrics getCircuitBreakerMetrics() {
        return resilienceService.getMetrics();
    }

    public io.github.resilience4j.circuitbreaker.CircuitBreaker.State getCircuitBreakerState() {
        return resilienceService.getCircuitBreakerState();
    }
}
