package com.cloudsync.service;

import com.cloudsync.cache.FileMetadataCacheService;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import com.cloudsync.config.CloudSyncProperties;
import com.cloudsync.dto.request.ChunkUploadRequest;
import com.cloudsync.dto.request.CompleteUploadRequest;
import com.cloudsync.dto.request.InitUploadRequest;
import com.cloudsync.dto.response.ChunkUploadResponse;
import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.dto.response.UploadCompleteResponse;
import com.cloudsync.dto.response.UploadInitResponse;
import com.cloudsync.exception.FileNotFoundException;
import com.cloudsync.exception.StorageQuotaExceededException;
import com.cloudsync.exception.UploadSessionExpiredException;
import com.cloudsync.model.entity.*;
import com.cloudsync.model.enums.FileStatus;
import com.cloudsync.model.enums.UploadStatus;
import com.cloudsync.partition.NodePartitionService;
import com.cloudsync.repository.*;
import com.cloudsync.s3.S3StorageService;
import com.cloudsync.util.FileUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadPartRepository uploadPartRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;
    private final NodePartitionService nodePartitionService;
    private final FileService fileService;
    private final AuditService auditService;
    private final CloudSyncProperties properties;
    private final FileMetadataCacheService cacheService;
    private final ObjectMapper objectMapper;

    private final Map<String, Path> tempFileMap = new ConcurrentHashMap<>();

    /**
     * Initialize a resumable upload session.
     * For small files, upload directly. For large files, initiate multipart upload.
     */
    @Transactional
    public UploadInitResponse initializeUpload(InitUploadRequest request, Long userId, Long organizationId) {
        log.info("Initializing upload for user: {}, file: {}, size: {}",
                userId, request.getFileName(), request.getFileSize());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Organization org = null;
        if (organizationId != null) {
            org = organizationRepository.findById(organizationId).orElse(null);
        } else if (user.getOrganization() != null) {
            org = user.getOrganization();
        }

        Long effectiveOrgId = org != null ? org.getId() : null;

        // Check storage quota
        if (org != null) {
            long usedBytes = org.getStorageUsedBytes() != null ? org.getStorageUsedBytes() : 0;
            long quotaBytes = org.getStorageQuotaBytes() != null ? org.getStorageQuotaBytes() : 0;
            if ((usedBytes + request.getFileSize()) > quotaBytes) {
                throw new StorageQuotaExceededException(
                        String.format("Storage quota exceeded. Available: %d bytes, Required: %d bytes",
                                quotaBytes - usedBytes, request.getFileSize()));
            }
        }

        String folderPath = "";
        if (request.getFolderId() != null) {
            Folder folder = folderRepository.findById(request.getFolderId()).orElse(null);
            if (folder != null) {
                folderPath = folder.getFolderPath();
            }
        }

        String s3Key = FileUtils.generateS3Key(
                String.valueOf(effectiveOrgId),
                String.valueOf(userId),
                request.getFileName(),
                folderPath
        );

        String sessionToken = FileUtils.generateSessionToken();
        String nodePartition = nodePartitionService.getNodeForFile(effectiveOrgId, userId, folderPath);

        // Determine chunk size based on file size
        long chunkSize = FileUtils.calculateChunkSize(request.getFileSize());
        int totalChunks = FileUtils.calculateTotalChunks(request.getFileSize(), chunkSize);

        String contentType = FileUtils.getContentType(request.getFileName(), request.getMimeType());
        String extension = FileUtils.getExtension(request.getFileName());

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        UploadSession session = UploadSession.builder()
                .sessionToken(sessionToken)
                .fileName(request.getFileName())
                .fileSizeBytes(request.getFileSize())
                .mimeType(request.getMimeType())
                .contentType(contentType)
                .chunkSizeBytes(chunkSize)
                .totalChunks(totalChunks)
                .uploadedChunks(0)
                .status(UploadStatus.INITIATED)
                .userId(userId)
                .organizationId(effectiveOrgId)
                .folderId(request.getFolderId())
                .destinationS3Key(s3Key)
                .fileHash(request.getFileHash())
                .expiresAt(expiresAt)
                .build();

        // For small files or non-resumable uploads, upload directly
        if (request.getFileSize() <= 10 * 1024 * 1024 && !Boolean.TRUE.equals(request.getResumable())) {
            log.info("Small file detected ({} bytes), skipping multipart upload setup", request.getFileSize());
            session.setUploadId("direct-" + UUID.randomUUID().toString());
        } else {
            // Initiate multipart upload in S3
            try {
                var initiateResponse = s3StorageService.initiateMultipartUpload(s3Key, contentType);
                session.setUploadId(initiateResponse.uploadId());

                // Pre-create part records
                List<UploadPart> parts = new ArrayList<>();
                for (int i = 1; i <= totalChunks; i++) {
                    long byteStart = (long) (i - 1) * chunkSize;
                    long byteEnd = Math.min(byteStart + chunkSize - 1, request.getFileSize() - 1);
                    UploadPart part = UploadPart.builder()
                            .uploadSession(session)
                            .partNumber(i)
                            .byteStart(byteStart)
                            .byteEnd(byteEnd)
                            .partSizeBytes(byteEnd - byteStart + 1)
                            .isUploaded(false)
                            .retryCount(0)
                            .build();
                    parts.add(part);
                }
                session.setParts(parts);
            } catch (CallNotPermittedException e) {
                log.error("Circuit breaker is open, cannot initiate multipart upload");
                throw new RuntimeException("S3 service temporarily unavailable. Please try again later.");
            }
        }

        session = uploadSessionRepository.save(session);

        // Save temp file path for accumulating chunks
        if (totalChunks > 1) {
            Path tempDir = Paths.get(properties.getUpload().getTempDirectory());
            try {
                Files.createDirectories(tempDir);
                Path tempFile = tempDir.resolve("upload-" + sessionToken);
                Files.createFile(tempFile);
                tempFileMap.put(sessionToken, tempFile);
            } catch (IOException e) {
                log.error("Failed to create temp file for session: {}", e.getMessage());
            }
        }

        log.info("Upload session initialized: {}, total chunks: {}", sessionToken, totalChunks);

        return UploadInitResponse.builder()
                .sessionToken(sessionToken)
                .uploadId(session.getUploadId())
                .fileName(request.getFileName())
                .fileSizeBytes(request.getFileSize())
                .totalChunks(totalChunks)
                .chunkSizeBytes(chunkSize)
                .resumable(totalChunks > 1)
                .expiresAt(expiresAt)
                .s3Key(s3Key)
                .build();
    }

    /**
     * Upload a single chunk of a multipart upload.
     */
    @Transactional
    public ChunkUploadResponse uploadChunk(ChunkUploadRequest request, byte[] chunkData, Long userId) {
        UploadSession session = uploadSessionRepository.findBySessionToken(request.getSessionToken())
                .orElseThrow(() -> new RuntimeException("Upload session not found: " + request.getSessionToken()));

        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized upload attempt");
        }

        if (session.isExpired()) {
            throw new UploadSessionExpiredException("Upload session has expired: " + session.getSessionToken());
        }

        if (session.getStatus() == UploadStatus.COMPLETED) {
            throw new IllegalStateException("Upload already completed");
        }

        int partNumber = request.getPartNumber();

        // Find or create the part record
        UploadPart part = uploadPartRepository.findByUploadSessionIdAndPartNumber(session.getId(), partNumber)
                .orElse(UploadPart.builder()
                        .uploadSession(session)
                        .partNumber(partNumber)
                        .byteStart(request.getByteStart() != null ? request.getByteStart() : (long) (partNumber - 1) * session.getChunkSizeBytes())
                        .isUploaded(false)
                        .retryCount(0)
                        .build());

        try {
            // For direct uploads (small files)
            if (session.getUploadId() != null && session.getUploadId().startsWith("direct-")) {
                Path tempFile = tempFileMap.get(session.getSessionToken());
                if (tempFile == null) {
                    tempFile = Paths.get(properties.getUpload().getTempDirectory(), "upload-" + session.getSessionToken());
                    try {
                        Files.createDirectories(tempFile.getParent());
                        Files.createFile(tempFile);
                        tempFileMap.put(session.getSessionToken(), tempFile);
                    } catch (IOException e) {
                        log.error("Failed to create temp file: {}", e.getMessage());
                    }
                }
                if (tempFile != null && Files.exists(tempFile)) {
                    try (FileOutputStream fos = new FileOutputStream(tempFile.toFile(), true)) {
                        fos.write(chunkData);
                    } catch (IOException e) {
                        log.error("Failed to write to temp file: {}", e.getMessage());
                        throw new RuntimeException("Failed to write chunk data", e);
                    }
                }

                part.setIsUploaded(true);
                part.setUploadedAt(LocalDateTime.now());
                part.setPartSizeBytes((long) chunkData.length);
            } else {
                // Multipart upload
                var uploadResponse = s3StorageService.uploadPart(
                        session.getDestinationS3Key(),
                        session.getUploadId(),
                        partNumber,
                        chunkData
                );

                part.setEtag(uploadResponse.eTag());
                part.setIsUploaded(true);
                part.setUploadedAt(LocalDateTime.now());
                part.setPartSizeBytes((long) chunkData.length);
            }

            uploadPartRepository.save(part);

            // Update session progress
            session.setUploadedChunks((int) uploadPartRepository.countUploadedPartsBySessionId(session.getId()));
            session.setLastChunkAt(LocalDateTime.now());

            if (session.getUploadedChunks() >= session.getTotalChunks()) {
                session.setStatus(UploadStatus.IN_PROGRESS);
            }

            uploadSessionRepository.save(session);

            log.debug("Chunk #{} uploaded for session: {}", partNumber, session.getSessionToken());

            return ChunkUploadResponse.builder()
                    .sessionToken(session.getSessionToken())
                    .partNumber(partNumber)
                    .etag(part.getEtag())
                    .uploadedChunks(session.getUploadedChunks())
                    .totalChunks(session.getTotalChunks())
                    .progressPercentage(session.getProgressPercentage())
                    .isComplete(session.isComplete())
                    .lastChunkAt(session.getLastChunkAt())
                    .build();

        } catch (CallNotPermittedException e) {
            log.error("Circuit breaker open during chunk upload");
            part.setRetryCount(part.getRetryCount() + 1);
            part.setErrorMessage("S3 service unavailable");
            uploadPartRepository.save(part);
            throw new RuntimeException("S3 service temporarily unavailable. Please retry.");
        }
    }

    /**
     * Complete a multipart upload and create the final file entity.
     */
    @Transactional
    public UploadCompleteResponse completeUpload(CompleteUploadRequest request, Long userId) {
        UploadSession session = uploadSessionRepository.findBySessionToken(request.getSessionToken())
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized upload completion");
        }

        if (session.isExpired()) {
            throw new UploadSessionExpiredException("Upload session has expired");
        }

        log.info("Completing upload session: {}", session.getSessionToken());

        String s3Key = session.getDestinationS3Key();
        List<CompletedPart> completedParts = new ArrayList<>();

        // For direct uploads (small files), just upload the temp file
        if (session.getUploadId() != null && session.getUploadId().startsWith("direct-")) {
            Path tempFile = tempFileMap.get(session.getSessionToken());
            if (tempFile != null && Files.exists(tempFile)) {
                try (InputStream is = Files.newInputStream(tempFile)) {
                    s3StorageService.uploadFile(s3Key, is, session.getFileSizeBytes(), session.getContentType());
                    Files.deleteIfExists(tempFile);
                    tempFileMap.remove(session.getSessionToken());
                } catch (IOException e) {
                    log.error("Failed to upload temp file: {}", e.getMessage());
                    throw new RuntimeException("Failed to upload file: " + e.getMessage());
                }
            }
        } else {
            // Multipart upload - assemble parts
            List<UploadPart> parts = uploadPartRepository.findByUploadSessionIdOrderByPartNumber(session.getId());

            for (UploadPart p : parts) {
                if (p.getEtag() != null) {
                    completedParts.add(CompletedPart.builder()
                            .partNumber(p.getPartNumber())
                            .eTag(p.getEtag())
                            .build());
                }
            }

            if (completedParts.isEmpty()) {
                throw new RuntimeException("No uploaded parts found");
            }

            // Sort parts by part number
            completedParts.sort(Comparator.comparingInt(CompletedPart::partNumber));

            var completeResponse = s3StorageService.completeMultipartUpload(
                    s3Key, session.getUploadId(), completedParts);

            log.info("Multipart upload completed: {}, ETag: {}", s3Key, completeResponse.eTag());
        }

        // Create file entity
        FileResponse fileResponse = fileService.createFileEntity(
                session.getFileName(),
                session.getFileSizeBytes(),
                session.getUserId(),
                session.getOrganizationId(),
                session.getFolderId(),
                s3Key,
                session.getContentType(),
                FileUtils.getExtension(session.getFileName())
        );

        // Mark session as completed
        session.setStatus(UploadStatus.COMPLETED);
        uploadSessionRepository.save(session);

        // Cleanup temp file
        Path tempFile = tempFileMap.remove(session.getSessionToken());
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", e.getMessage());
            }
        }

        auditService.logAction(userId, session.getOrganizationId(), "FILE_UPLOAD_COMPLETE", "File",
                fileResponse.getId(), session.getFileName(), null, null);

        String downloadUrl = fileResponse.getDownloadUrl();

        log.info("Upload completed successfully: {} -> file id: {}", session.getSessionToken(), fileResponse.getId());

        return UploadCompleteResponse.builder()
                .sessionToken(session.getSessionToken())
                .fileId(fileResponse.getId())
                .fileName(session.getFileName())
                .fileSizeBytes(session.getFileSizeBytes())
                .s3Key(s3Key)
                .completed(true)
                .completedAt(LocalDateTime.now())
                .downloadUrl(downloadUrl)
                .message("File uploaded and stored successfully")
                .build();
    }

    /**
     * Get the status of an upload session (for resuming).
     */
    @Transactional(readOnly = true)
    public ChunkUploadResponse getUploadStatus(String sessionToken, Long userId) {
        UploadSession session = uploadSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to upload session");
        }

        List<UploadPart> uploadedParts = uploadPartRepository.findByUploadSessionIdOrderByPartNumber(session.getId());

        int actualUploaded = (int) uploadedParts.stream().filter(UploadPart::getIsUploaded).count();

        return ChunkUploadResponse.builder()
                .sessionToken(session.getSessionToken())
                .partNumber(null)
                .etag(null)
                .uploadedChunks(actualUploaded)
                .totalChunks(session.getTotalChunks())
                .progressPercentage(session.getTotalChunks() > 0 ? (actualUploaded * 100.0) / session.getTotalChunks() : 0)
                .isComplete(session.isComplete())
                .lastChunkAt(session.getLastChunkAt())
                .build();
    }

    /**
     * Get list of pending chunks for resumption.
     */
    @Transactional(readOnly = true)
    public List<Integer> getPendingChunks(String sessionToken, Long userId) {
        UploadSession session = uploadSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access");
        }

        List<UploadPart> pendingParts = uploadPartRepository.findByUploadSessionIdAndIsUploadedFalse(session.getId());

        return pendingParts.stream()
                .map(UploadPart::getPartNumber)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Cancel an upload session and cleanup.
     */
    @Transactional
    public void cancelUpload(String sessionToken, Long userId) {
        UploadSession session = uploadSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        if (!session.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access");
        }

        if (!session.getUploadId().startsWith("direct-")) {
            s3StorageService.abortMultipartUpload(session.getDestinationS3Key(), session.getUploadId());
        }

        session.setStatus(UploadStatus.CANCELLED);
        uploadSessionRepository.save(session);

        // Cleanup temp file
        Path tempFile = tempFileMap.remove(sessionToken);
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", e.getMessage());
            }
        }

        log.info("Upload cancelled: {}", sessionToken);
    }

    /**
     * Scheduled cleanup of expired upload sessions.
     */
    @Scheduled(fixedRateString = "${cloudsync.upload.cleanup-interval:3600}000")
    @Transactional
    public void cleanupExpiredSessions() {
        List<UploadSession> expiredSessions = uploadSessionRepository.findExpiredByStatus(UploadStatus.INITIATED);
        expiredSessions.addAll(uploadSessionRepository.findExpiredByStatus(UploadStatus.IN_PROGRESS));

        for (UploadSession session : expiredSessions) {
            try {
                if (session.getUploadId() != null && !session.getUploadId().startsWith("direct-")) {
                    s3StorageService.abortMultipartUpload(session.getDestinationS3Key(), session.getUploadId());
                }
                session.setStatus(UploadStatus.FAILED);
                session.setErrorMessage("Session expired");
                uploadSessionRepository.save(session);

                Path tempFile = tempFileMap.remove(session.getSessionToken());
                if (tempFile != null) {
                    Files.deleteIfExists(tempFile);
                }

                log.info("Cleaned up expired session: {}", session.getSessionToken());
            } catch (Exception e) {
                log.error("Failed to cleanup session {}: {}", session.getSessionToken(), e.getMessage());
            }
        }

        // Delete sessions older than 7 days
        int deleted = uploadSessionRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(7));
        if (deleted > 0) {
            log.info("Deleted {} old upload sessions", deleted);
        }
    }

    /**
     * Upload file directly (non-resumable, for simple uploads).
     */
    @Transactional
    public FileResponse uploadDirect(MultipartFile file, Long folderId, Long userId, Long organizationId) {
        try {
            String fileName = FileUtils.sanitizeFileName(file.getOriginalFilename());
            String s3Key = FileUtils.generateS3Key(
                    String.valueOf(organizationId),
                    String.valueOf(userId),
                    fileName,
                    null
            );

            var response = s3StorageService.uploadFile(s3Key, file);

            FileResponse fileResponse = fileService.createFileEntity(
                    fileName,
                    file.getSize(),
                    userId,
                    organizationId,
                    folderId,
                    s3Key,
                    file.getContentType(),
                    FileUtils.getExtension(fileName)
            );

            auditService.logAction(userId, organizationId, "FILE_UPLOAD_DIRECT", "File",
                    fileResponse.getId(), fileName, null, null);

            return fileResponse;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

}
