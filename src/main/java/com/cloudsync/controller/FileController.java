package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.dto.common.PageResponse;
import com.cloudsync.dto.request.FileSearchRequest;
import com.cloudsync.dto.response.DashboardResponse;
import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.security.filter.AuthenticatedUser;
import com.cloudsync.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cloudsync.util.RequestContext;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File management APIs")
public class FileController {

    private final FileService fileService;

    @GetMapping("/{fileId}")
    @Operation(summary = "Get file details", description = "Get metadata for a specific file")
    public ResponseEntity<ApiResponse<FileResponse>> getFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FileResponse response = fileService.getFile(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List files", description = "List files in root or in a specific folder")
    public ResponseEntity<ApiResponse<PageResponse<FileResponse>>> listFiles(
            @Parameter(description = "Folder ID (null for root)")
            @RequestParam(required = false) Long folderId,
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort order (ASC/DESC)")
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        PageResponse<FileResponse> response = fileService.getFiles(user.getUserId(), folderId, page, size, sortBy, sortOrder);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/search")
    @Operation(summary = "Search files", description = "Search files by name")
    public ResponseEntity<ApiResponse<PageResponse<FileResponse>>> searchFiles(
            @Parameter(description = "Search query")
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FileSearchRequest request = FileSearchRequest.builder()
                .query(query)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();
        PageResponse<FileResponse> response = fileService.searchFiles(request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/trash")
    @Operation(summary = "List deleted files", description = "Get files in trash")
    public ResponseEntity<ApiResponse<PageResponse<FileResponse>>> getTrash(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        PageResponse<FileResponse> response = fileService.getDeletedFiles(user.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard", description = "Get storage dashboard for current user")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        DashboardResponse response = fileService.getDashboard(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{fileId}/download")
    @Operation(summary = "Download file", description = "Stream file download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));

        FileResponse fileInfo = fileService.getFile(fileId, user.getUserId());
        InputStream fileStream = fileService.downloadFile(fileId, user.getUserId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileInfo.getName() + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getMimeType() != null ? fileInfo.getMimeType() : "application/octet-stream"))
                .contentLength(fileInfo.getFileSizeBytes())
                .body(new InputStreamResource(fileStream));
    }

    @GetMapping("/{fileId}/url")
    @Operation(summary = "Get download URL", description = "Get a pre-signed download URL for the file")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        String url = fileService.getDownloadUrl(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(url));
    }

    @PatchMapping("/{fileId}")
    @Operation(summary = "Rename file", description = "Update file name")
    public ResponseEntity<ApiResponse<FileResponse>> renameFile(
            @PathVariable Long fileId,
            @RequestParam String newName,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FileResponse response = fileService.updateFileMetadata(fileId, user.getUserId(), newName);
        return ResponseEntity.ok(ApiResponse.success(response, "File renamed successfully"));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "Delete file (soft)", description = "Move file to trash")
    public ResponseEntity<ApiResponse<FileResponse>> deleteFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FileResponse response = fileService.deleteFile(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "File moved to trash"));
    }

    @PostMapping("/{fileId}/restore")
    @Operation(summary = "Restore file", description = "Restore a file from trash")
    public ResponseEntity<ApiResponse<FileResponse>> restoreFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FileResponse response = fileService.restoreFile(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "File restored successfully"));
    }

    @DeleteMapping("/{fileId}/permanent")
    @Operation(summary = "Permanently delete file", description = "Permanently delete a file (cannot be undone)")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        fileService.permanentlyDeleteFile(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "File permanently deleted"));
    }
}
