package com.cloudsync.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSearchRequest {

    private String query;
    private String name;
    private String extension;
    private String mimeType;
    private Long folderId;
    private Long organizationId;
    private String sortBy;
    private String sortOrder;
    private Integer page;
    private Integer size;
}
