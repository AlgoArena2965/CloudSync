package com.cloudsync.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitResponse {
    private String sessionToken;
    private String uploadId;
    private String fileName;
    private Long fileSizeBytes;
    private Integer totalChunks;
    private Long chunkSizeBytes;
    private Boolean resumable;
    private java.time.LocalDateTime expiresAt;
    private String s3Key;
}
