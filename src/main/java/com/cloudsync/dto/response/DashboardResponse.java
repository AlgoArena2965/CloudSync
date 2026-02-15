package com.cloudsync.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private Long totalFiles;
    private Long totalFolders;
    private Long totalStorageUsedBytes;
    private Long totalStorageQuotaBytes;
    private Double usagePercentage;
    private Long totalShares;
    private Long activeShares;
    private Long totalDownloads;
    private Long recentFileCount;
    private java.util.List<FileResponse> recentFiles;
}
