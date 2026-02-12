package com.cloudsync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadRequest {

    @NotBlank(message = "File name is required")
    private String fileName;

    @Positive(message = "File size must be positive")
    private Long fileSize;

    private String mimeType;

    private Long folderId;

    private Long organizationId;

    private String contentType;

    private String fileHash;

    private Boolean resumable;
}
