package com.cloudsync.dto.response;

import com.cloudsync.model.enums.ShareType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareResponse {
    private Long id;
    private Long fileId;
    private String fileName;
    private String shareToken;
    private ShareType shareType;
    private Long sharedByUserId;
    private String sharedByUserName;
    private Long sharedWithUserId;
    private String sharedWithUserName;
    private String shareUrl;
    private Integer maxDownloads;
    private Integer currentDownloads;
    private LocalDateTime expiresAt;
    private Boolean allowPreview;
    private Boolean allowEdit;
    private Long viewCount;
    private Boolean isActive;
    private Boolean isExpired;
    private LocalDateTime createdAt;
}
