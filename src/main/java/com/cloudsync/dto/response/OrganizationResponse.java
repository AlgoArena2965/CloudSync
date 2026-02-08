package com.cloudsync.dto.response;

import com.cloudsync.model.enums.OrganizationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long storageQuotaBytes;
    private Long storageUsedBytes;
    private Double usagePercentage;
    private OrganizationStatus status;
    private Integer maxUsers;
    private Integer currentUserCount;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;
}
