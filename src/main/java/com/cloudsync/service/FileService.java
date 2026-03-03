package com.cloudsync.service;

import com.cloudsync.cache.FileMetadataCacheService;
import com.cloudsync.config.CloudSyncProperties;
import com.cloudsync.dto.common.PageResponse;
import com.cloudsync.dto.request.FileSearchRequest;
import com.cloudsync.dto.response.DashboardResponse;
import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.exception.AccessDeniedException;
import com.cloudsync.exception.FileNotFoundException;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.FileEntity;
import com.cloudsync.model.entity.Folder;
import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.FileStatus;
import com.cloudsync.partition.NodePartitionService;
import com.cloudsync.repository.FileRepository;
import com.cloudsync.repository.FolderRepository;
import com.cloudsync.repository.OrganizationRepository;
import com.cloudsync.repository.UserRepository;
import com.cloudsync.s3.S3StorageService;
import com.cloudsync.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;
    private final NodePartitionService nodePartitionService;
    private final FileMetadataCacheService cacheService;
    private final AuditService auditService;
    private final CloudSyncProperties properties;

    @Transactional(readOnly = true)
    public FileResponse getFile(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        var cached = cacheService.getCachedFileMetadata(fileId);
        if (cached.isPresent()) {
            return cached.get();
        }

        FileResponse response = mapToResponse(file);
        cacheService.cacheFileMetadata(fileId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public FileResponse getFileByS3Key(String s3Key) {
        FileEntity file = fileRepository.findByS3Key(s3Key)
                .orElseThrow(() -> new FileNotFoundException("File not found with s3 key: " + s3Key));
        return mapToResponse(file);
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> getFiles(Long userId, Long folderId, int page, int size, String sortBy, String sortOrder) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder != null ? sortOrder : "DESC"),
                sortBy != null ? sortBy : "createdAt");

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<FileEntity> filePage;

        if (folderId == null) {
            filePage = fileRepository.findByOwnerIdAndFolderIsNull(userId, pageable);
        } else {
            filePage = fileRepository.findByOwnerIdAndFolderId(userId, folderId, pageable);
        }

        return PageResponse.from(filePage, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> searchFiles(FileSearchRequest request, Long userId) {
        Sort sort = Sort.by(Sort.Direction.fromString(
                request.getSortOrder() != null ? request.getSortOrder() : "DESC"),
                request.getSortBy() != null ? request.getSortBy() : "createdAt");

        Pageable pageable = PageRequest.of(
                request.getPage() != null ? request.getPage() : 0,
                request.getSize() != null ? request.getSize() : 20,
                sort);

        Page<FileEntity> filePage = fileRepository.searchByName(request.getQuery(), userId, pageable);
        return PageResponse.from(filePage, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> getDeletedFiles(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FileEntity> deletedPage = fileRepository.findDeletedByOwnerId(userId, pageable);
        return PageResponse.from(deletedPage, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        var cached = cacheService.getCachedDashboard(userId, DashboardResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        long totalFiles = fileRepository.countByOwnerId(userId);
        long totalFolders = folderRepository.countByOwnerId(userId);
        Long storageUsed = fileRepository.sumStorageUsedByOwnerId(userId);

        User user = userRepository.findById(userId).orElse(null);
        Long quotaBytes = null;
        if (user != null && user.getOrganization() != null) {
            Organization org = user.getOrganization();
            quotaBytes = org.getStorageQuotaBytes();
        }

        Page<FileEntity> recentFiles = fileRepository.findRecentByOwnerId(userId, PageRequest.of(0, 5));
        List<FileResponse> recentFileResponses = recentFiles.getContent().stream()
                .map(this::mapToResponse).collect(Collectors.toList());

        double usagePercent = 0.0;
        if (quotaBytes != null && quotaBytes > 0 && storageUsed != null) {
            usagePercent = (storageUsed * 100.0) / quotaBytes;
        }

        DashboardResponse response = DashboardResponse.builder()
                .totalFiles(totalFiles)
                .totalFolders(totalFolders)
                .totalStorageUsedBytes(storageUsed != null ? storageUsed : 0L)
                .totalStorageQuotaBytes(quotaBytes != null ? quotaBytes : 0L)
                .usagePercentage(Math.round(usagePercent * 100.0) / 100.0)
                .recentFileCount((long) recentFileResponses.size())
                .recentFiles(recentFileResponses)
                .build();

        cacheService.cacheDashboard(userId, response);
        return response;
    }

    @Transactional
    public FileResponse deleteFile(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        file.setIsDeleted(true);
        file.setDeletedAt(LocalDateTime.now());
        file.setStatus(FileStatus.DELETED);
        Long savedFileSize = file.getFileSizeBytes();
        String savedS3Key = file.getS3Key();
        Long savedOrgId = file.getOrganization() != null ? file.getOrganization().getId() : null;
        String savedFileName = file.getName();
        file = fileRepository.save(file);

        userRepository.findById(userId).ifPresent(user -> {
            long currentUsed = user.getStorageUsedBytes() != null ? user.getStorageUsedBytes() : 0;
            long fileSize = savedFileSize != null ? savedFileSize : 0;
            user.setStorageUsedBytes(Math.max(0, currentUsed - fileSize));
            userRepository.save(user);
        });

        cacheService.evictFileMetadata(fileId, savedS3Key);
        cacheService.evictDashboard(userId);

        auditService.logAction(userId, file.getOrganization() != null ? file.getOrganization().getId() : null,
                "FILE_DELETE", "File", fileId, file.getName(), null, null);

        log.info("File soft-deleted: {} (id: {})", file.getName(), fileId);
        return mapToResponse(file);
    }

    @Transactional
    public void permanentlyDeleteFile(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        try {
            s3StorageService.deleteFile(file.getS3Key());
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", e.getMessage());
        }

        fileRepository.delete(file);

        cacheService.evictFileMetadata(fileId, file.getS3Key());

        auditService.logAction(userId, null, "FILE_PERMANENT_DELETE", "File",
                fileId, file.getName(), null, null);

        log.info("File permanently deleted: {} (id: {})", file.getName(), fileId);
    }

    @Transactional
    public FileResponse restoreFile(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        if (!file.getIsDeleted()) {
            throw new IllegalStateException("File is not deleted");
        }

        file.setIsDeleted(false);
        file.setDeletedAt(null);
        file.setStatus(FileStatus.COMPLETED);
        Long restoredFileSize = file.getFileSizeBytes();
        String restoredS3Key = file.getS3Key();
        file = fileRepository.save(file);

        userRepository.findById(userId).ifPresent(user -> {
            long currentUsed = user.getStorageUsedBytes() != null ? user.getStorageUsedBytes() : 0;
            long fileSize = restoredFileSize != null ? restoredFileSize : 0;
            user.setStorageUsedBytes(currentUsed + fileSize);
            userRepository.save(user);
        });

        cacheService.evictFileMetadata(fileId, restoredS3Key);
        cacheService.evictDashboard(userId);

        log.info("File restored: {} (id: {})", file.getName(), fileId);
        return mapToResponse(file);
    }

    @Transactional(readOnly = true)
    public InputStream downloadFile(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        if (file.getIsDeleted()) {
            throw new FileNotFoundException("File has been deleted");
        }

        fileRepository.incrementDownloadCount(fileId);
        file.setDownloadCount(file.getDownloadCount() + 1);
        file.setLastAccessedAt(LocalDateTime.now());

        cacheService.evictFileMetadata(fileId, file.getS3Key());

        auditService.logAction(userId, file.getOrganization() != null ? file.getOrganization().getId() : null,
                "FILE_DOWNLOAD", "File", fileId, file.getName(), null, null);

        return s3StorageService.downloadFile(file.getS3Key());
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        String url = s3StorageService.generatePresignedDownloadUrl(file.getS3Key(), Duration.ofMinutes(60));

        auditService.logAction(userId, null, "FILE_DOWNLOAD_URL", "File",
                fileId, file.getName(), null, null);

        return url;
    }

    @Transactional
    public void incrementDownloadCount(Long fileId) {
        fileRepository.incrementDownloadCount(fileId);
        cacheService.evictFileMetadata(fileId, null);
    }

    @Transactional
    public FileResponse updateFileMetadata(Long fileId, Long userId, String newName) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        String oldName = file.getName();
        file.setName(newName);
        file.setExtension(FileUtils.getExtension(newName));
        file = fileRepository.save(file);

        cacheService.evictFileMetadata(fileId, file.getS3Key());

        auditService.logAction(userId, null, "FILE_RENAME", "File",
                fileId, file.getName(), oldName, newName);

        return mapToResponse(file);
    }

    public FileResponse createFileEntity(String fileName, Long fileSize, Long userId, Long orgId,
                                          Long folderId, String s3Key, String mimeType, String extension) {
        String node = nodePartitionService.getNodeForFile(orgId, userId, null);

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId).orElse(null);
        }

        Organization org = null;
        if (orgId != null) {
            org = organizationRepository.findById(orgId).orElse(null);
        }

        FileEntity file = FileEntity.builder()
                .name(fileName)
                .originalName(fileName)
                .mimeType(mimeType)
                .fileSizeBytes(fileSize)
                .s3Key(s3Key)
                .s3Bucket(s3StorageService.getBucketName())
                .status(FileStatus.COMPLETED)
                .folder(folder)
                .ownerId(userId)
                .organization(org)
                .extension(extension)
                .contentType(mimeType)
                .isDeleted(false)
                .isShared(false)
                .downloadCount(0L)
                .version(1)
                .nodePartition(node)
                .build();

        file = fileRepository.save(file);

        userRepository.findById(userId).ifPresent(user -> {
            long currentUsed = user.getStorageUsedBytes() != null ? user.getStorageUsedBytes() : 0;
            user.setStorageUsedBytes(currentUsed + fileSize);
            userRepository.save(user);
        });

        if (org != null) {
            long orgUsed = org.getStorageUsedBytes() != null ? org.getStorageUsedBytes() : 0;
            org.setStorageUsedBytes(orgUsed + fileSize);
            organizationRepository.save(org);
        }

        cacheService.evictDashboard(userId);

        return mapToResponse(file);
    }

    private FileResponse mapToResponse(FileEntity file) {
        String downloadUrl = null;
        String previewUrl = null;
        try {
            downloadUrl = s3StorageService.generatePresignedDownloadUrl(
                    file.getS3Key(), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.debug("Could not generate download URL for file: {}", file.getId());
        }

        return FileResponse.builder()
                .id(file.getId())
                .name(file.getName())
                .originalName(file.getOriginalName())
                .mimeType(file.getMimeType())
                .fileSizeBytes(file.getFileSizeBytes())
                .storageSizeBytes(file.getStorageSizeBytes())
                .status(file.getStatus())
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .folderName(file.getFolder() != null ? file.getFolder().getName() : null)
                .folderPath(file.getFolder() != null ? file.getFolder().getFolderPath() : null)
                .ownerId(file.getOwnerId())
                .organizationId(file.getOrganization() != null ? file.getOrganization().getId() : null)
                .organizationName(file.getOrganization() != null ? file.getOrganization().getName() : null)
                .fileHash(file.getFileHash())
                .contentType(file.getContentType())
                .extension(file.getExtension())
                .isDeleted(file.getIsDeleted())
                .isShared(file.getIsShared())
                .downloadCount(file.getDownloadCount())
                .lastAccessedAt(file.getLastAccessedAt())
                .version(file.getVersion())
                .nodePartition(file.getNodePartition())
                .downloadUrl(downloadUrl)
                .previewUrl(previewUrl)
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}
