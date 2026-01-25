package com.cloudsync.model.entity;

import com.cloudsync.model.enums.UploadStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "upload_sessions", indexes = {
        @Index(name = "idx_upload_session_token", columnList = "session_token"),
        @Index(name = "idx_upload_user", columnList = "user_id"),
        @Index(name = "idx_upload_status", columnList = "status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class UploadSession extends BaseEntity {

    @Column(name = "session_token", nullable = false, unique = true)
    private String sessionToken;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "chunk_size_bytes")
    private Long chunkSizeBytes;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Column(name = "uploaded_chunks")
    @Builder.Default
    private Integer uploadedChunks = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UploadStatus status = UploadStatus.INITIATED;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "destination_s3_key")
    private String destinationS3Key;

    @Column(name = "upload_id")
    private String uploadId;

    @Column(name = "parts_info", columnDefinition = "TEXT")
    private String partsInfo;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "temp_file_path")
    private String tempFilePath;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_chunk_at")
    private LocalDateTime lastChunkAt;

    @OneToMany(mappedBy = "uploadSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UploadPart> parts = new ArrayList<>();

    public double getProgressPercentage() {
        if (totalChunks == null || totalChunks == 0) return 0.0;
        return (uploadedChunks * 100.0) / totalChunks;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isComplete() {
        return uploadedChunks != null && totalChunks != null && uploadedChunks >= totalChunks;
    }

    public void addPart(UploadPart part) {
        parts.add(part);
        part.setUploadSession(this);
    }
}
