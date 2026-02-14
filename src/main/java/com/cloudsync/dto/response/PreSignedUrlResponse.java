package com.cloudsync.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreSignedUrlResponse {
    private String fileId;
    private String s3Key;
    private String uploadUrl;
    private String downloadUrl;
    private java.time.LocalDateTime expiresAt;
    private Long expiresInSeconds;
    private String httpMethod;
}
