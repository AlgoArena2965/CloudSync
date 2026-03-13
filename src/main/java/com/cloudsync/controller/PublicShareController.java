package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.dto.response.ShareResponse;
import com.cloudsync.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/public/share")
@RequiredArgsConstructor
@Tag(name = "Public Share", description = "Public APIs for accessing shared files")
public class PublicShareController {

    private final ShareService shareService;

    @GetMapping("/{shareToken}")
    @Operation(summary = "Get shared file info", description = "Get information about a shared file via share token")
    public ResponseEntity<ApiResponse<ShareResponse>> getSharedFile(@PathVariable String shareToken) {
        ShareResponse response = shareService.getShareByToken(shareToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{shareToken}/download")
    @Operation(summary = "Download shared file", description = "Download a shared file using the share token")
    public ResponseEntity<InputStreamResource> downloadSharedFile(@PathVariable String shareToken) {
        ShareResponse shareInfo = shareService.getShareByToken(shareToken);
        InputStream fileStream = shareService.downloadSharedFile(shareToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + shareInfo.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(fileStream));
    }

    @GetMapping("/{shareToken}/url")
    @Operation(summary = "Get shared file download URL", description = "Get a pre-signed download URL for a shared file")
    public ResponseEntity<ApiResponse<String>> getSharedFileUrl(@PathVariable String shareToken) {
        String url = shareService.getSharedFileDownloadUrl(shareToken);
        return ResponseEntity.ok(ApiResponse.success(url));
    }

    @PostMapping("/{shareToken}/view")
    @Operation(summary = "Record view", description = "Record a view on a shared file")
    public ResponseEntity<ApiResponse<Void>> recordView(@PathVariable String shareToken) {
        shareService.recordView(shareToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
