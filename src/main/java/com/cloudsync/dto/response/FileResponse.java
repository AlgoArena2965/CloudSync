package com.cloudsync.dto.response;

import com.cloudsync.model.enums.FileStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {
    private Long id;
    private String name;
    private String originalName;
    private String mimeType;
    private Long fileSizeBytes;
    private Long storageSizeBytes;
    private FileStatus status;
    private Long folderId;
    private String folderName;
    private String folderPath;
    private Long ownerId;
    private String ownerName;
    private Long organizationId;
    private String organizationName;
    private String fileHash;
    private String contentType;
    private String extension;
    private Boolean isDeleted;
    private Boolean isShared;
    private Long downloadCount;
    private String s3Key;
    private LocalDateTime lastAccessedAt;
    private Integer version;
    private String nodePartition;
    private String downloadUrl;
    private String previewUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
