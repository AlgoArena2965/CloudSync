package com.cloudsync.dto.request;

import com.cloudsync.model.enums.ShareType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShareRequest {

    @NotNull(message = "File ID is required")
    private Long fileId;

    private ShareType shareType;

    private Long sharedWithUserId;

    private String password;

    private Integer maxDownloads;

    private LocalDateTime expiresAt;

    private Boolean allowPreview;

    private Boolean allowEdit;
}
