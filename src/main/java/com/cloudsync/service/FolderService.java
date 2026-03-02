package com.cloudsync.service;

import com.cloudsync.cache.FileMetadataCacheService;
import com.cloudsync.dto.request.CreateFolderRequest;
import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.dto.response.FolderResponse;
import com.cloudsync.exception.DuplicateResourceException;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.FileEntity;
import com.cloudsync.model.entity.Folder;
import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.FileStatus;
import com.cloudsync.repository.FileRepository;
import com.cloudsync.repository.FolderRepository;
import com.cloudsync.repository.OrganizationRepository;
import com.cloudsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final FileMetadataCacheService cacheService;
    private final AuditService auditService;

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request, Long userId, Long organizationId) {
        log.info("Creating folder: {} for user: {}", request.getName(), userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Organization org = null;
        if (organizationId != null) {
            org = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
        } else if (user.getOrganization() != null) {
            org = user.getOrganization();
        }

        Long effectiveOrgId = org != null ? org.getId() : null;

        Folder parentFolder = null;
        String parentPath = "";

        if (request.getParentFolderId() != null) {
            parentFolder = folderRepository.findByIdAndOwnerId(request.getParentFolderId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder", "id", request.getParentFolderId()));
            parentPath = parentFolder.getFolderPath();
        }

        String folderPath = (parentPath.isEmpty() ? "" : parentPath + "/") + request.getName();

        if (folderRepository.findByOwnerIdAndNameAndParentId(userId, request.getName(),
                request.getParentFolderId()).isPresent()) {
            throw new DuplicateResourceException("Folder with name '" + request.getName() + "' already exists in this location");
        }

        Folder folder = Folder.builder()
                .name(request.getName())
                .parentFolder(parentFolder)
                .ownerId(userId)
                .organization(org)
                .folderPath(folderPath)
                .isShared(false)
                .totalSizeBytes(0L)
                .fileCount(0)
                .build();

        folder = folderRepository.save(folder);

        cacheService.evictFolderMetadata(folder.getId(), folderPath);

        auditService.logAction(userId, effectiveOrgId, "FOLDER_CREATE", "Folder",
                folder.getId(), folder.getName(), null, null);

        log.info("Folder created: {} (path: {})", folder.getName(), folder.getFolderPath());
        return mapToResponse(folder);
    }

    @Transactional(readOnly = true)
    public FolderResponse getFolder(Long folderId, Long userId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", "id", folderId));

        var cached = cacheService.getCachedFolderMetadata(folderId);
        if (cached.isPresent()) {
            return cached.get();
        }

        FolderResponse response = mapToResponse(folder);
        cacheService.cacheFolderMetadata(folderId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> getRootFolders(Long userId) {
        return folderRepository.findByOwnerIdAndParentFolderIsNull(userId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> getSubFolders(Long parentFolderId, Long userId) {
        return folderRepository.findByOwnerIdAndParentFolderId(userId, parentFolderId)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FileResponse> getFilesInFolder(Long folderId, Long userId) {
        folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", "id", folderId));

        return fileRepository.findByFolderIdAndNotDeleted(folderId)
                .stream().map(this::mapFileToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FileResponse> getFilesInRoot(Long userId) {
        return fileRepository.findByOwnerIdAndFolderIsNull(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream().map(this::mapFileToResponse).collect(Collectors.toList());
    }

    @Transactional
    public FolderResponse renameFolder(Long folderId, String newName, Long userId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", "id", folderId));

        String oldPath = folder.getFolderPath();
        String newPath = (folder.getParentFolder() != null ? folder.getParentFolder().getFolderPath() + "/" : "") + newName;

        folder.setName(newName);
        folder.setFolderPath(newPath);
        folder = folderRepository.save(folder);

        List<Folder> descendants = folderRepository.findAllDescendants(oldPath + "/", userId);
        for (Folder child : descendants) {
            child.setFolderPath(child.getFolderPath().replace(oldPath, newPath));
            folderRepository.save(child);
        }

        cacheService.evictFolderMetadata(folderId, oldPath);

        auditService.logAction(userId, folder.getOrganization() != null ? folder.getOrganization().getId() : null,
                "FOLDER_RENAME", "Folder", folderId, folder.getName(), oldPath, newPath);

        return mapToResponse(folder);
    }

    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder", "id", folderId));

        Long orgId = folder.getOrganization() != null ? folder.getOrganization().getId() : null;

        List<FileEntity> files = fileRepository.findByFolderId(folderId);
        for (FileEntity file : files) {
            file.setIsDeleted(true);
            fileRepository.save(file);
        }

        folderRepository.delete(folder);

        cacheService.evictFolderMetadata(folderId, folder.getFolderPath());

        auditService.logAction(userId, orgId, "FOLDER_DELETE", "Folder",
                folderId, folder.getName(), null, null);

        log.info("Folder deleted: {} (id: {})", folder.getName(), folderId);
    }

    private FolderResponse mapToResponse(Folder folder) {
        List<FileEntity> files = fileRepository.findByFolderId(folder.getId());

        List<FileResponse> fileResponses = files.stream()
                .filter(f -> !f.getIsDeleted())
                .map(this::mapFileToResponse)
                .collect(Collectors.toList());

        List<Folder> subFolders = folderRepository.findByOwnerIdAndParentFolderId(
                folder.getOwnerId(), folder.getId());

        long totalSize = files.stream()
                .filter(f -> !f.getIsDeleted())
                .mapToLong(f -> f.getFileSizeBytes() != null ? f.getFileSizeBytes() : 0)
                .sum();

        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .parentFolderPath(folder.getParentFolder() != null ? folder.getParentFolder().getFolderPath() : null)
                .ownerId(folder.getOwnerId())
                .organizationId(folder.getOrganization() != null ? folder.getOrganization().getId() : null)
                .organizationName(folder.getOrganization() != null ? folder.getOrganization().getName() : null)
                .folderPath(folder.getFolderPath())
                .isShared(folder.getIsShared())
                .totalSizeBytes(totalSize)
                .fileCount((int) files.stream().filter(f -> !f.getIsDeleted()).count())
                .subFolderCount(subFolders.size())
                .files(fileResponses)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }

    private FileResponse mapFileToResponse(FileEntity file) {
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
                .extension(file.getExtension())
                .isDeleted(file.getIsDeleted())
                .isShared(file.getIsShared())
                .downloadCount(file.getDownloadCount())
                .version(file.getVersion())
                .nodePartition(file.getNodePartition())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
}
