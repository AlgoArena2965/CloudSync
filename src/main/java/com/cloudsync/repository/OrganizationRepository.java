package com.cloudsync.repository;

import com.cloudsync.model.entity.Organization;
import com.cloudsync.model.enums.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlug(String slug);

    Optional<Organization> findByName(String name);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    List<Organization> findByStatus(OrganizationStatus status);

    @Modifying
    @Query("UPDATE Organization o SET o.storageUsedBytes = :storageBytes WHERE o.id = :orgId")
    void updateStorageUsedBytes(@Param("orgId") Long orgId, @Param("storageBytes") Long storageBytes);

    @Modifying
    @Query("UPDATE Organization o SET o.status = :status WHERE o.id = :orgId")
    void updateStatus(@Param("orgId") Long orgId, @Param("status") OrganizationStatus status);
}
