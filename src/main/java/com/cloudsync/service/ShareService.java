package com.cloudsync.service;

import com.cloudsync.cache.FileMetadataCacheService;
import com.cloudsync.dto.request.CreateShareRequest;
import com.cloudsync.dto.response.ShareResponse;
import com.cloudsync.exception.AccessDeniedException;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.FileEntity;
import com.cloudsync.model.entity.FileShare;
import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.ShareType;
import com.cloudsync.repository.FileRepository;
import com.cloudsync.repository.FileShareRepository;
import com.cloudsync.repository.UserRepository;
import com.cloudsync.s3.S3StorageService;
import com.cloudsync.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShareService {

    private final FileShareRepository fileShareRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileMetadataCacheService cacheService;
    private final AuditService auditService;
    private final S3StorageService s3StorageService;

    @Transactional
    public ShareResponse createShare(CreateShareRequest request, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(request.getFileId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", request.getFileId()));

        if (file.getIsDeleted()) {
            throw new IllegalStateException("Cannot share a deleted file");
        }

        String shareToken = FileUtils.generateShareToken();

        User sharedWith = null;
        if (request.getSharedWithUserId() != null) {
            sharedWith = userRepository.findById(request.getSharedWithUserId()).orElse(null);
        }

        ShareType shareType = request.getShareType() != null ? request.getShareType() : ShareType.INTERNAL;

        FileShare share = FileShare.builder()
                .file(file)
                .shareToken(shareToken)
                .shareType(shareType)
                .sharedByUserId(userId)
                .sharedWith(sharedWith)
                .sharePassword(request.getPassword())
                .maxDownloads(request.getMaxDownloads())
                .currentDownloads(0)
                .expiresAt(request.getExpiresAt())
                .allowPreview(request.getAllowPreview() != null ? request.getAllowPreview() : true)
                .allowEdit(request.getAllowEdit() != null ? request.getAllowEdit() : false)
                .isActive(true)
                .build();

        share = fileShareRepository.save(share);

        // Mark file as shared
        file.setIsShared(true);
        file.setShareToken(shareToken);
        fileRepository.save(file);

        User sharer = userRepository.findById(userId).orElse(null);

        cacheService.evictShareCache(shareToken);
        cacheService.evictFileMetadata(file.getId(), file.getS3Key());

        auditService.logAction(userId, file.getOrganization() != null ? file.getOrganization().getId() : null,
                "FILE_SHARE_CREATE", "File", file.getId(), file.getName(), null, null);

        log.info("Share created for file: {} by user: {}", file.getName(), userId);

        return mapToResponse(share, sharer, sharedWith);
    }

    @Transactional(readOnly = true)
    public ShareResponse getShareByToken(String shareToken) {
        FileShare share = fileShareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        if (!share.getIsActive()) {
            throw new IllegalStateException("This share link has been deactivated");
        }

        if (share.isExpired()) {
            throw new IllegalStateException("This share link has expired");
        }

        return mapToResponse(share, null, share.getSharedWith());
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> getSharesForFile(Long fileId, Long userId) {
        FileEntity file = fileRepository.findByIdAndOwnerId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", fileId));

        return fileShareRepository.findByFileId(fileId).stream()
                .map(share -> mapToResponse(share, null, share.getSharedWith()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> getSharesByUser(Long userId) {
        return fileShareRepository.findBySharedByUserId(userId).stream()
                .map(share -> mapToResponse(share, null, share.getSharedWith()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deactivateShare(Long shareId, Long userId) {
        FileShare share = fileShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share", "id", shareId));

        if (!share.getSharedByUserId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to deactivate this share");
        }

        share.setIsActive(false);
        fileShareRepository.save(share);

        cacheService.evictShareCache(share.getShareToken());

        auditService.logAction(userId, null, "SHARE_DEACTIVATE", "FileShare",
                shareId, null, null, null);

        log.info("Share deactivated: {}", shareId);
    }

    @Transactional
    public void recordDownload(String shareToken) {
        FileShare share = fileShareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        if (share.hasReachedDownloadLimit()) {
            throw new IllegalStateException("Download limit reached for this share");
        }

        if (share.isExpired()) {
            throw new IllegalStateException("Share has expired");
        }

        fileShareRepository.incrementDownloadCount(share.getId());
        cacheService.evictShareCache(shareToken);
    }

    @Transactional
    public void recordView(String shareToken) {
        FileShare share = fileShareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        fileShareRepository.incrementViewCount(share.getId());
        cacheService.evictShareCache(shareToken);
    }

    @Transactional(readOnly = true)
    public InputStream downloadSharedFile(String shareToken) {
        FileShare share = fileShareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        if (!share.getIsActive()) {
            throw new IllegalStateException("Share link is deactivated");
        }

        if (share.isExpired()) {
            throw new IllegalStateException("Share link has expired");
        }

        if (share.hasReachedDownloadLimit()) {
            throw new IllegalStateException("Download limit reached");
        }

        fileShareRepository.incrementDownloadCount(share.getId());

        return s3StorageService.downloadFile(share.getFile().getS3Key());
    }

    @Transactional(readOnly = true)
    public String getSharedFileDownloadUrl(String shareToken) {
        FileShare share = fileShareRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share not found"));

        if (!share.getIsActive() || share.isExpired()) {
            throw new IllegalStateException("Share link is not available");
        }

        return s3StorageService.generatePresignedDownloadUrl(
                share.getFile().getS3Key(), Duration.ofHours(24));
    }

    private ShareResponse mapToResponse(FileShare share, User sharer, User sharedWith) {
        String shareUrl = "/api/public/share/" + share.getShareToken();

        String sharerName = null;
        String sharedWithName = null;

        if (sharer != null) {
            sharerName = sharer.getFullName();
        } else {
            User s = userRepository.findById(share.getSharedByUserId()).orElse(null);
            if (s != null) sharerName = s.getFullName();
        }

        if (sharedWith != null) {
            sharedWithName = sharedWith.getFullName();
        } else if (share.getSharedWith() != null) {
            sharedWithName = share.getSharedWith().getFullName();
        }

        return ShareResponse.builder()
                .id(share.getId())
                .fileId(share.getFile().getId())
                .fileName(share.getFile().getName())
                .shareToken(share.getShareToken())
                .shareType(share.getShareType())
                .sharedByUserId(share.getSharedByUserId())
                .sharedByUserName(sharerName)
                .sharedWithUserId(share.getSharedWith() != null ? share.getSharedWith().getId() : null)
                .sharedWithUserName(sharedWithName)
                .shareUrl(shareUrl)
                .maxDownloads(share.getMaxDownloads())
                .currentDownloads(share.getCurrentDownloads())
                .expiresAt(share.getExpiresAt())
                .allowPreview(share.getAllowPreview())
                .allowEdit(share.getAllowEdit())
                .viewCount(share.getViewCount())
                .isActive(share.getIsActive())
                .isExpired(share.isExpired())
                .createdAt(share.getCreatedAt())
                .build();
    }
}
