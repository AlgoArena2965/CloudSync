package com.cloudsync.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponse {
    private Long id;
    private String name;
    private Long parentFolderId;
    private String parentFolderPath;
    private Long ownerId;
    private String ownerName;
    private Long organizationId;
    private String organizationName;
    private String folderPath;
    private Boolean isShared;
    private Long totalSizeBytes;
    private Integer fileCount;
    private Integer subFolderCount;
    private List<FolderResponse> subFolders;
    private List<FileResponse> files;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
