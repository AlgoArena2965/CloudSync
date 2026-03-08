package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.dto.common.PageResponse;
import com.cloudsync.dto.request.CreateOrganizationRequest;
import com.cloudsync.dto.response.OrganizationResponse;
import com.cloudsync.security.filter.AuthenticatedUser;
import com.cloudsync.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cloudsync.util.RequestContext;

import java.util.List;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management APIs")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @Operation(summary = "Create organization", description = "Create a new organization")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        OrganizationResponse response = organizationService.createOrganization(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Organization created"));
    }

    @GetMapping("/{organizationId}")
    @Operation(summary = "Get organization", description = "Get organization details")
    public ResponseEntity<ApiResponse<OrganizationResponse>> getOrganization(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        OrganizationResponse response = organizationService.getOrganization(organizationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List organizations", description = "List all organizations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> listOrganizations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<OrganizationResponse> response = PageResponse.from(
                organizationService.getAllOrganizations(pageable),
                org -> org);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{organizationId}")
    @Operation(summary = "Update organization", description = "Update organization details")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest) {
        RequestContext.setCurrent(RequestContext.fromHttpRequest(httpRequest, user.getUserId(), user.getOrganizationId()));
        OrganizationResponse response = organizationService.updateOrganization(organizationId, request, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Organization updated"));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active organizations", description = "Get list of active organizations (for registration)")
    public ResponseEntity<ApiResponse<List<OrganizationResponse>>> getActiveOrganizations(
            HttpServletRequest httpRequest) {
        List<OrganizationResponse> response = organizationService.getActiveOrganizations();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
