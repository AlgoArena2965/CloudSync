package com.cloudsync.service;

import com.cloudsync.exception.DuplicateResourceException;
import com.cloudsync.exception.ResourceNotFoundException;
import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.OrganizationStatus;
import com.cloudsync.model.enums.Role;
import com.cloudsync.repository.OrganizationRepository;
import com.cloudsync.repository.UserRepository;
import com.cloudsync.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cloudsync.dto.request.RegisterRequest;
import com.cloudsync.dto.response.AuthResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, organizationRepository, passwordEncoder,
                jwtService, authenticationManager, auditService);
    }

    @Test
    @DisplayName("Should register new user successfully")
    void testRegisterSuccess() {
        RegisterRequest request = RegisterRequest.builder()
                .email("newuser@example.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900000L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getUser());
        assertEquals("newuser@example.com", response.getUser().getEmail());

        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(auditService).logAction(any(), any(), eq("USER_REGISTER"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject duplicate email registration")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .password("Password123!")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get current user")
    void testGetCurrentUser() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.ROLE_USER)
                .isEnabled(true)
                .isEmailVerified(false)
                .storageUsedBytes(0L)
                .build();

        when(userRepository.findByIdWithOrganization(1L)).thenReturn(Optional.of(user));

        var response = authService.getCurrentUser(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("user@test.com", response.getEmail());
        assertEquals("Test User", response.getFullName());
    }

    @Test
    @DisplayName("Should throw exception for non-existent user")
    void testGetCurrentUserNotFound() {
        when(userRepository.findByIdWithOrganization(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser(999L));
    }

    @Test
    @DisplayName("Should change password successfully")
    void testChangePassword() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .password("oldEncodedPassword")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword123!", "oldEncodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("newEncodedPassword");

        com.cloudsync.dto.request.ChangePasswordRequest request =
                com.cloudsync.dto.request.ChangePasswordRequest.builder()
                        .currentPassword("OldPassword123!")
                        .newPassword("NewPassword123!")
                        .build();

        assertDoesNotThrow(() -> authService.changePassword(1L, request));
        verify(userRepository).save(any(User.class));
    }
}
