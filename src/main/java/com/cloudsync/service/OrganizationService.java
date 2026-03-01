package com.cloudsync.service;

import com.cloudsync.cache.FileMetadataCacheService;
import com.cloudsync.dto.request.CreateOrganizationRequest;
import com.cloudsync.dto.response.OrganizationResponse;
import com.cloudsync.exception.DuplicateResourceException;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.OrganizationStatus;
import com.cloudsync.repository.OrganizationRepository;
import com.cloudsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final FileMetadataCacheService cacheService;
    private final AuditService auditService;

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, Long createdByUserId) {
        log.info("Creating organization: {}", request.getName());

        if (organizationRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Organization with name '" + request.getName() + "' already exists");
        }

        String slug = generateSlug(request.getName());
        if (organizationRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Organization with slug '" + slug + "' already exists");
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .status(OrganizationStatus.ACTIVE)
                .storageQuotaBytes(request.getStorageQuotaBytes() != null ? request.getStorageQuotaBytes() : 10L * 1024 * 1024 * 1024)
                .storageUsedBytes(0L)
                .maxUsers(request.getMaxUsers() != null ? request.getMaxUsers() : 50)
                .ownerId(createdByUserId)
                .build();

        org = organizationRepository.save(org);

        auditService.logAction(createdByUserId, org.getId(), "ORG_CREATE", "Organization",
                org.getId(), org.getName(), null, null);

        log.info("Organization created: {} (slug: {})", org.getName(), org.getSlug());
        return mapToResponse(org);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "organizationCache", key = "#organizationId")
    public OrganizationResponse getOrganization(Long organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
        return mapToResponse(org);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationBySlug(String slug) {
        Organization org = organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "slug", slug));
        return mapToResponse(org);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> getAllOrganizations(Pageable pageable) {
        return organizationRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getActiveOrganizations() {
        return organizationRepository.findByStatus(OrganizationStatus.ACTIVE)
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public OrganizationResponse updateOrganization(Long organizationId, CreateOrganizationRequest request, Long userId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        if (request.getName() != null && !request.getName().equals(org.getName())) {
            if (organizationRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("Organization name already taken");
            }
            org.setName(request.getName());
        }

        if (request.getDescription() != null) {
            org.setDescription(request.getDescription());
        }
        if (request.getStorageQuotaBytes() != null) {
            org.setStorageQuotaBytes(request.getStorageQuotaBytes());
        }
        if (request.getMaxUsers() != null) {
            org.setMaxUsers(request.getMaxUsers());
        }

        org = organizationRepository.save(org);
        cacheService.evictOrganizationCaches(organizationId);

        auditService.logAction(userId, organizationId, "ORG_UPDATE", "Organization",
                organizationId, org.getName(), null, null);

        return mapToResponse(org);
    }

    @Transactional
    public void updateStorageUsed(Long organizationId, Long storageBytes) {
        organizationRepository.updateStorageUsedBytes(organizationId, storageBytes);
        cacheService.evictOrganizationCaches(organizationId);
    }

    @Transactional
    public void updateStatus(Long organizationId, OrganizationStatus status, Long userId) {
        organizationRepository.updateStatus(organizationId, status);
        cacheService.evictOrganizationCaches(organizationId);
        auditService.logAction(userId, organizationId, "ORG_STATUS_CHANGE", "Organization",
                organizationId, null, null, status.name());
    }

    @Transactional(readOnly = true)
    public boolean hasAvailableQuota(Long organizationId, long additionalBytes) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        long used = org.getStorageUsedBytes() != null ? org.getStorageUsedBytes() : 0;
        long quota = org.getStorageQuotaBytes() != null ? org.getStorageQuotaBytes() : 0;

        return (used + additionalBytes) <= quota;
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .substring(0, Math.min(name.length(), 50));
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private OrganizationResponse mapToResponse(Organization org) {
        int userCount = (int) userRepository.countByOrganizationId(org.getId());

        double usagePercent = 0.0;
        if (org.getStorageQuotaBytes() != null && org.getStorageQuotaBytes() > 0) {
            long used = org.getStorageUsedBytes() != null ? org.getStorageUsedBytes() : 0;
            usagePercent = (used * 100.0) / org.getStorageQuotaBytes();
        }

        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .description(org.getDescription())
                .storageQuotaBytes(org.getStorageQuotaBytes())
                .storageUsedBytes(org.getStorageUsedBytes())
                .usagePercentage(Math.round(usagePercent * 100.0) / 100.0)
                .status(org.getStatus())
                .maxUsers(org.getMaxUsers())
                .currentUserCount(userCount)
                .ownerId(org.getOwnerId())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
