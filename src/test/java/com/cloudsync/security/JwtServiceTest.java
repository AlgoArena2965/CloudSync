package com.cloudsync.security;

import com.cloudsync.security.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "testSecretKeyForUnitTestsThatIsLongEnough256Bits1234567890");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiry", 604800000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "cloudsync-test");
    }

    @Test
    @DisplayName("Should generate valid access token")
    void testGenerateAccessToken() {
        String token = jwtService.generateAccessToken(1L, "test@example.com", "ROLE_USER", 100L);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals("test@example.com", jwtService.extractEmail(token));
    }

    @Test
    @DisplayName("Should generate valid refresh token")
    void testGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(1L, "test@example.com");
        assertNotNull(token);
        assertEquals("test@example.com", jwtService.extractEmail(token));
    }

    @Test
    @DisplayName("Should extract user ID from token")
    void testExtractUserId() {
        String token = jwtService.generateAccessToken(42L, "user@test.com", "ROLE_USER", null);
        assertEquals(42L, jwtService.extractUserId(token));
    }

    @Test
    @DisplayName("Should extract role from token")
    void testExtractRole() {
        String token = jwtService.generateAccessToken(1L, "admin@test.com", "ROLE_ORG_ADMIN", 1L);
        assertEquals("ROLE_ORG_ADMIN", jwtService.extractRole(token));
    }

    @Test
    @DisplayName("Should extract organization ID from token")
    void testExtractOrganizationId() {
        String token = jwtService.generateAccessToken(1L, "user@test.com", "ROLE_USER", 55L);
        assertEquals(55L, jwtService.extractOrganizationId(token));
    }

    @Test
    @DisplayName("Should validate token correctly")
    void testTokenValidation() {
        String token = jwtService.generateAccessToken(1L, "valid@test.com", "ROLE_USER", null);
        assertTrue(jwtService.isTokenValid(token, "valid@test.com"));
        assertFalse(jwtService.isTokenValid(token, "wrong@test.com"));
    }

    @Test
    @DisplayName("Should detect expired tokens")
    void testTokenExpiry() {
        // Token from setup should not be expired
        String token = jwtService.generateAccessToken(1L, "user@test.com", "ROLE_USER", null);
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("Should handle null organization ID")
    void testNullOrganizationId() {
        String token = jwtService.generateAccessToken(1L, "user@test.com", "ROLE_USER", null);
        assertNull(jwtService.extractOrganizationId(token));
    }
}
