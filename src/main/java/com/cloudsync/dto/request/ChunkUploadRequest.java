package com.cloudsync.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadRequest {

    @NotBlank(message = "Session token is required")
    private String sessionToken;

    @NotNull(message = "Part number is required")
    @PositiveOrZero(message = "Part number must be non-negative")
    private Integer partNumber;

    private Long partSize;

    private Long byteStart;

    private Long byteEnd;

    private String checksum;
}
