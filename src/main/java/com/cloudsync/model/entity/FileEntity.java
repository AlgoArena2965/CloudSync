package com.cloudsync.model.entity;

import com.cloudsync.model.enums.FileStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_file_folder", columnList = "folder_id"),
        @Index(name = "idx_file_owner", columnList = "owner_id"),
        @Index(name = "idx_file_org", columnList = "organization_id"),
        @Index(name = "idx_file_s3_key", columnList = "s3_key"),
        @Index(name = "idx_file_name", columnList = "name")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class FileEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_size_bytes")
    private Long storageSizeBytes;

    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FileStatus status = FileStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "extension")
    private String extension;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_shared")
    @Builder.Default
    private Boolean isShared = false;

    @Column(name = "share_token")
    private String shareToken;

    @Column(name = "download_count")
    @Builder.Default
    private Long downloadCount = 0L;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "previous_version_key")
    private String previousVersionKey;

    @Column(name = "node_partition")
    private String nodePartition;

    public String getFullPath() {
        if (folder != null && folder.getFolderPath() != null) {
            return folder.getFolderPath() + "/" + name;
        }
        return name;
    }
}
