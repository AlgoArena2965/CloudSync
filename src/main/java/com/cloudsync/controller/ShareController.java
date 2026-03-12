package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.dto.request.CreateShareRequest;
import com.cloudsync.dto.response.ShareResponse;
import com.cloudsync.security.filter.AuthenticatedUser;
import com.cloudsync.service.ShareService;
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
@RequestMapping("/shares")
@RequiredArgsConstructor
@Tag(name = "Shares", description = "File sharing APIs")
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    @Operation(summary = "Create share", description = "Create a share link for a file")
    public ResponseEntity<ApiResponse<ShareResponse>> createShare(
            @Valid @RequestBody CreateShareRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        ShareResponse response = shareService.createShare(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Share link created"));
    }

    @GetMapping("/file/{fileId}")
    @Operation(summary = "Get shares for file", description = "Get all share links for a specific file")
    public ResponseEntity<ApiResponse<List<ShareResponse>>> getSharesForFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<ShareResponse> response = shareService.getSharesForFile(fileId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my shares", description = "Get all shares created by the current user")
    public ResponseEntity<ApiResponse<List<ShareResponse>>> getMyShares(
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        List<ShareResponse> response = shareService.getSharesByUser(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "Deactivate share", description = "Deactivate a share link")
    public ResponseEntity<ApiResponse<Void>> deactivateShare(
            @PathVariable Long shareId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        shareService.deactivateShare(shareId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Share deactivated"));
    }
}
