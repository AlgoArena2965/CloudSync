package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.dto.request.CreateFolderRequest;
import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.dto.response.FolderResponse;
import com.cloudsync.security.filter.AuthenticatedUser;
import com.cloudsync.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cloudsync.util.RequestContext;

import java.util.List;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
@Tag(name = "Folders", description = "Folder management APIs")
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    @Operation(summary = "Create folder", description = "Create a new folder")
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FolderResponse response = folderService.createFolder(request, user.getUserId(), user.getOrganizationId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Folder created successfully"));
    }

    @GetMapping("/{folderId}")
    @Operation(summary = "Get folder", description = "Get folder details with contents")
    public ResponseEntity<ApiResponse<FolderResponse>> getFolder(
            @PathVariable Long folderId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FolderResponse response = folderService.getFolder(folderId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/root")
    @Operation(summary = "Get root folders", description = "Get folders at the root level")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getRootFolders(
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<FolderResponse> response = folderService.getRootFolders(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{parentId}/children")
    @Operation(summary = "Get sub-folders", description = "Get sub-folders within a parent folder")
    public ResponseEntity<ApiResponse<List<FolderResponse>>> getSubFolders(
            @PathVariable Long parentId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<FolderResponse> response = folderService.getSubFolders(parentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{folderId}/files")
    @Operation(summary = "Get files in folder", description = "List all files in a folder")
    public ResponseEntity<ApiResponse<List<FileResponse>>> getFilesInFolder(
            @PathVariable Long folderId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<FileResponse> response = folderService.getFilesInFolder(folderId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/files/root")
    @Operation(summary = "Get root files", description = "List all files at the root level")
    public ResponseEntity<ApiResponse<List<FileResponse>>> getRootFiles(
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<FileResponse> response = folderService.getFilesInRoot(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{folderId}/rename")
    @Operation(summary = "Rename folder", description = "Rename a folder")
    public ResponseEntity<ApiResponse<FolderResponse>> renameFolder(
            @PathVariable Long folderId,
            @RequestParam String newName,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        FolderResponse response = folderService.renameFolder(folderId, newName, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Folder renamed successfully"));
    }

    @DeleteMapping("/{folderId}")
    @Operation(summary = "Delete folder", description = "Delete a folder and move all files to trash")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(
            @PathVariable Long folderId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        folderService.deleteFolder(folderId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Folder deleted successfully"));
    }
}
