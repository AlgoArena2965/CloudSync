package com.cloudsync.repository;

import com.cloudsync.model.entity.User;
import com.cloudsync.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByOrganizationId(Long organizationId);

    Page<User> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization WHERE u.email = :email")
    Optional<User> findByEmailWithOrganization(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization WHERE u.id = :id")
    Optional<User> findByIdWithOrganization(@Param("id") Long id);

    @Modifying
    @Query("UPDATE User u SET u.storageUsedBytes = :storageBytes WHERE u.id = :userId")
    void updateStorageUsedBytes(@Param("userId") Long userId, @Param("storageBytes") Long storageBytes);

    @Modifying
    @Query("UPDATE User u SET u.refreshToken = :token, u.refreshTokenExpiry = :expiry WHERE u.id = :userId")
    void updateRefreshToken(@Param("userId") Long userId, @Param("token") String token,
            @Param("expiry") java.time.LocalDateTime expiry);

    @Modifying
    @Query("UPDATE User u SET u.refreshToken = null, u.refreshTokenExpiry = null WHERE u.id = :userId")
    void clearRefreshToken(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.refreshToken = null WHERE u.refreshToken = :token")
    void clearRefreshTokenByToken(@Param("token") String token);

    List<User> findByRole(Role role);

    @Query("SELECT COUNT(u) FROM User u WHERE u.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") Long organizationId);

    @Query("SELECT u FROM User u WHERE u.organization.id = :orgId AND u.role = :role")
    List<User> findByOrganizationIdAndRole(@Param("orgId") Long organizationId, @Param("role") Role role);
}
