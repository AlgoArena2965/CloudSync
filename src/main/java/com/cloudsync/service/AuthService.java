package com.cloudsync.service;

import com.cloudsync.dto.request.*;
import com.cloudsync.dto.response.AuthResponse;
import com.cloudsync.dto.response.UserResponse;
import com.cloudsync.exception.AuthenticationException;
import com.cloudsync.exception.DuplicateResourceException;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.OrganizationStatus;
import com.cloudsync.model.enums.Role;
import com.cloudsync.repository.OrganizationRepository;
import com.cloudsync.repository.UserRepository;
import com.cloudsync.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.ROLE_USER)
                .isEnabled(true)
                .isEmailVerified(false)
                .build();

        Organization org = null;
        if (request.getOrganizationId() != null) {
            org = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", request.getOrganizationId()));

            if (org.getStatus() != OrganizationStatus.ACTIVE) {
                throw new AuthenticationException("Organization is not active");
            }

            user.setOrganization(org);
            user.setRole(Role.ROLE_USER);
        } else if (request.getOrganizationName() != null && !request.getOrganizationName().isEmpty()) {
            String slug = generateSlug(request.getOrganizationName());
            if (organizationRepository.existsByName(request.getOrganizationName()) ||
                    organizationRepository.existsBySlug(slug)) {
                throw new DuplicateResourceException("Organization already exists: " + request.getOrganizationName());
            }

            org = Organization.builder()
                    .name(request.getOrganizationName())
                    .slug(slug)
                    .status(OrganizationStatus.ACTIVE)
                    .storageQuotaBytes(5L * 1024 * 1024 * 1024)
                    .maxUsers(10)
                    .storageUsedBytes(0L)
                    .build();
            org = organizationRepository.save(org);

            user.setOrganization(org);
            user.setRole(Role.ROLE_ORG_ADMIN);
            org.setOwnerId(null);
            org = organizationRepository.save(org);
        }

        user = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                org != null ? org.getId() : null
        );

        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(7));
        userRepository.save(user);

        auditService.logAction(user.getId(), org != null ? org.getId() : null,
                "USER_REGISTER", "User", user.getId(), user.getEmail(), null, null);

        log.info("User registered successfully: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiry() / 1000)
                .user(mapToUserResponse(user, org))
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("User login attempt: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase().trim(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmailWithOrganization(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!user.getIsEnabled()) {
            throw new AuthenticationException("Account is disabled");
        }

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getOrganization() != null ? user.getOrganization().getId() : null
        );

        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(Boolean.TRUE.equals(request.getRememberMe()) ? 30 : 7));
        userRepository.save(user);

        auditService.logAction(user.getId(),
                user.getOrganization() != null ? user.getOrganization().getId() : null,
                "USER_LOGIN", "User", user.getId(), user.getEmail(), null, null);

        log.info("User logged in successfully: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiry() / 1000)
                .user(mapToUserResponse(user, user.getOrganization()))
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isTokenValid(refreshToken, "")) {
            throw new AuthenticationException("Invalid refresh token");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmailWithOrganization(email)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new AuthenticationException("Refresh token does not match");
        }

        if (user.getRefreshTokenExpiry() == null || user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new AuthenticationException("Refresh token has expired");
        }

        String newAccessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getOrganization() != null ? user.getOrganization().getId() : null
        );

        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(7));
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiry() / 1000)
                .user(mapToUserResponse(user, user.getOrganization()))
                .build();
    }

    @Transactional
    public void logout(Long userId) {
        userRepository.clearRefreshToken(userId);
        auditService.logAction(userId, null, "USER_LOGOUT", "User", userId, null, null, null);
        log.info("User logged out: {}", userId);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AuthenticationException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(null);
        userRepository.save(user);

        auditService.logAction(userId, user.getOrganization() != null ? user.getOrganization().getId() : null,
                "PASSWORD_CHANGE", "User", userId, null, null, null);
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findByIdWithOrganization(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return mapToUserResponse(user, user.getOrganization());
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .substring(0, Math.min(name.length(), 50))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UserResponse mapToUserResponse(User user, Organization org) {
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
