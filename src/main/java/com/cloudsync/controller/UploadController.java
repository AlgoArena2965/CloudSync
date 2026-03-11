package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.dto.request.ChunkUploadRequest;
import com.cloudsync.dto.request.CompleteUploadRequest;
import com.cloudsync.dto.request.InitUploadRequest;
import com.cloudsync.dto.response.ChunkUploadResponse;
import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.dto.response.UploadCompleteResponse;
import com.cloudsync.dto.response.UploadInitResponse;
import com.cloudsync.security.filter.AuthenticatedUser;
import com.cloudsync.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.cloudsync.util.RequestContext;

import java.util.List;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
@Tag(name = "Upload", description = "File upload APIs with resumable multipart support")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/init")
    @Operation(summary = "Initialize upload", description = "Initialize a new upload session (resumable multipart)")
    public ResponseEntity<ApiResponse<UploadInitResponse>> initializeUpload(
            @Valid @RequestBody InitUploadRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));

        Long orgId = request.getOrganizationId() != null ? request.getOrganizationId() : user.getOrganizationId();
        UploadInitResponse response = uploadService.initializeUpload(request, user.getUserId(), orgId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Upload session initialized"));
    }

    @PostMapping("/chunk")
    @Operation(summary = "Upload chunk", description = "Upload a single chunk of a multipart upload")
    public ResponseEntity<ApiResponse<ChunkUploadResponse>> uploadChunk(
            @RequestParam("sessionToken") String sessionToken,
            @RequestParam("partNumber") Integer partNumber,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));

        ChunkUploadRequest request = ChunkUploadRequest.builder()
                .sessionToken(sessionToken)
                .partNumber(partNumber)
                .build();

        try {
            ChunkUploadResponse response = uploadService.uploadChunk(
                    request,
                    file.getBytes(),
                    user.getUserId()
            );
            return ResponseEntity.ok(ApiResponse.success(response, "Chunk uploaded successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Chunk upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/chunk/binary")
    @Operation(summary = "Upload chunk (binary)", description = "Upload a chunk as binary data with headers")
    public ResponseEntity<ApiResponse<ChunkUploadResponse>> uploadChunkBinary(
            @RequestHeader("X-Session-Token") String sessionToken,
            @RequestHeader("X-Part-Number") Integer partNumber,
            @RequestBody byte[] data,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));

        ChunkUploadRequest request = ChunkUploadRequest.builder()
                .sessionToken(sessionToken)
                .partNumber(partNumber)
                .build();

        ChunkUploadResponse response = uploadService.uploadChunk(request, data, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete upload", description = "Complete a multipart upload and finalize the file")
    public ResponseEntity<ApiResponse<UploadCompleteResponse>> completeUpload(
            @Valid @RequestBody CompleteUploadRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        UploadCompleteResponse response = uploadService.completeUpload(request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Upload completed successfully"));
    }

    @GetMapping("/status/{sessionToken}")
    @Operation(summary = "Get upload status", description = "Get current status of an upload session (for resumption)")
    public ResponseEntity<ApiResponse<ChunkUploadResponse>> getUploadStatus(
            @PathVariable String sessionToken,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        ChunkUploadResponse response = uploadService.getUploadStatus(sessionToken, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/pending/{sessionToken}")
    @Operation(summary = "Get pending chunks", description = "Get list of chunks that haven't been uploaded yet (for resumption)")
    public ResponseEntity<ApiResponse<List<Integer>>> getPendingChunks(
            @PathVariable String sessionToken,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<Integer> pending = uploadService.getPendingChunks(sessionToken, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    @PostMapping("/cancel/{sessionToken}")
    @Operation(summary = "Cancel upload", description = "Cancel an in-progress upload and cleanup")
    public ResponseEntity<ApiResponse<Void>> cancelUpload(
            @PathVariable String sessionToken,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        uploadService.cancelUpload(sessionToken, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Upload cancelled"));
    }

    @PostMapping(value = "/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Direct upload", description = "Upload a file directly (for small files, non-resumable)")
    public ResponseEntity<ApiResponse<FileResponse>> uploadDirect(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FileResponse response = uploadService.uploadDirect(file, folderId, user.getUserId(), user.getOrganizationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "File uploaded successfully"));
    }
}
