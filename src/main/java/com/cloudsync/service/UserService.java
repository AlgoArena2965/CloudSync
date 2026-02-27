package com.cloudsync.service;

import com.cloudsync.dto.response.UserResponse;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.entity.User;
import com.cloudsync.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Cacheable(value = "userCache", key = "#userId")
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findByIdWithOrganization(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return mapToResponse(user, user.getOrganization());
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByOrganization(Long organizationId, Pageable pageable) {
        return userRepository.findByOrganizationId(organizationId, pageable)
                .map(user -> mapToResponse(user, user.getOrganization()));
    }

    @Transactional
    public void updateStorageUsed(Long userId, Long storageBytes) {
        userRepository.updateStorageUsedBytes(userId, storageBytes);
    }

    @Transactional
    public void addStorageUsed(Long userId, long deltaBytes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        long current = user.getStorageUsedBytes() != null ? user.getStorageUsedBytes() : 0;
        user.setStorageUsedBytes(current + deltaBytes);
        userRepository.save(user);
    }

    @Transactional
    public void subtractStorageUsed(Long userId, long bytes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        long current = user.getStorageUsedBytes() != null ? user.getStorageUsedBytes() : 0;
        user.setStorageUsedBytes(Math.max(0, current - bytes));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserResponse mapToResponse(User user, Organization org) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isEnabled(user.getIsEnabled())
                .isEmailVerified(user.getIsEmailVerified())
                .storageUsedBytes(user.getStorageUsedBytes())
                .organizationId(org != null ? org.getId() : null)
                .organizationName(org != null ? org.getName() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
