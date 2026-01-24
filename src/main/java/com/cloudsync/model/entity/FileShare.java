package com.cloudsync.model.entity;

import com.cloudsync.model.enums.ShareType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_shares", indexes = {
        @Index(name = "idx_share_token", columnList = "share_token"),
        @Index(name = "idx_share_file", columnList = "file_id"),
        @Index(name = "idx_share_expiry", columnList = "expires_at")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class FileShare extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @Column(name = "share_token", nullable = false, unique = true)
    private String shareToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShareType shareType = ShareType.INTERNAL;

    @Column(name = "shared_by_user_id", nullable = false)
    private Long sharedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_user_id", insertable = false, updatable = false)
    private User sharedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_with_user_id")
    private User sharedWith;

    @Column(name = "share_password")
    private String sharePassword;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "current_downloads")
    @Builder.Default
    private Integer currentDownloads = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "allow_preview")
    @Builder.Default
    private Boolean allowPreview = true;

    @Column(name = "allow_edit")
    @Builder.Default
    private Boolean allowEdit = false;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasReachedDownloadLimit() {
        return maxDownloads != null && currentDownloads >= maxDownloads;
    }
}
