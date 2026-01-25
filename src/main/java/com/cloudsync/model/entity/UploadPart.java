package com.cloudsync.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_parts", indexes = {
        @Index(name = "idx_part_session", columnList = "upload_session_id"),
        @Index(name = "idx_part_number", columnList = "part_number")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class UploadPart extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_session_id", nullable = false)
    private UploadSession uploadSession;

    @Column(name = "part_number", nullable = false)
    private Integer partNumber;

    @Column(name = "etag")
    private String etag;

    @Column(name = "part_size_bytes")
    private Long partSizeBytes;

    @Column(name = "byte_start")
    private Long byteStart;

    @Column(name = "byte_end")
    private Long byteEnd;

    @Column(name = "is_uploaded")
    @Builder.Default
    private Boolean isUploaded = false;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "temp_chunk_path")
    private String tempChunkPath;

    @Column(name = "checksum")
    private String checksum;
}
