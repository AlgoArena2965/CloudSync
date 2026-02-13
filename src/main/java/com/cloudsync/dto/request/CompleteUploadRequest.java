package com.cloudsync.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

    @NotBlank(message = "Session token is required")
    private String sessionToken;

    private String fileHash;
}
