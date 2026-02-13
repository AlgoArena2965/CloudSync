package com.cloudsync.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadCompleteResponse {
    private String sessionToken;
    private Long fileId;
    private String fileName;
    private Long fileSizeBytes;
    private String s3Key;
    private Boolean completed;
    private java.time.LocalDateTime completedAt;
    private String downloadUrl;
    private String message;
}
