package com.cloudsync.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadResponse {
    private String sessionToken;
    private Integer partNumber;
    private String etag;
    private Integer uploadedChunks;
    private Integer totalChunks;
    private Double progressPercentage;
    private Boolean isComplete;
    private java.time.LocalDateTime lastChunkAt;
}
