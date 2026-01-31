package com.cloudsync.repository;

import com.cloudsync.model.entity.FileEntity;
import com.cloudsync.model.enums.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    List<FileEntity> findByOwnerId(Long ownerId);

    Page<FileEntity> findByOwnerId(Long ownerId, Pageable pageable);

    Page<FileEntity> findByOwnerIdAndFolderIsNull(Long ownerId, Pageable pageable);

    Page<FileEntity> findByOwnerIdAndFolderId(Long ownerId, Long folderId, Pageable pageable);

    Page<FileEntity> findByOrganizationId(Long organizationId, Pageable pageable);

    Optional<FileEntity> findByS3Key(String s3Key);

    Optional<FileEntity> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<FileEntity> findByShareToken(String shareToken);

    List<FileEntity> findByFolderId(Long folderId);

    List<FileEntity> findByOrganizationIdAndIsDeletedFalse(Long organizationId);

    Page<FileEntity> findByOrganizationIdAndIsDeletedFalse(Long organizationId, Pageable pageable);

    @Query("SELECT f FROM FileEntity f WHERE f.ownerId = :ownerId AND f.isDeleted = true")
    Page<FileEntity> findDeletedByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT f FROM FileEntity f WHERE f.ownerId = :ownerId AND f.isDeleted = true")
    List<FileEntity> findAllDeletedByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT f FROM FileEntity f WHERE f.name LIKE %:query% AND f.ownerId = :ownerId AND f.isDeleted = false")
    Page<FileEntity> searchByName(@Param("query") String query, @Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT f FROM FileEntity f WHERE f.organization.id = :orgId AND f.status = :status")
    List<FileEntity> findByOrganizationIdAndStatus(@Param("orgId") Long orgId, @Param("status") FileStatus status);

    @Modifying
    @Query("UPDATE FileEntity f SET f.status = :status WHERE f.id = :fileId")
    void updateStatus(@Param("fileId") Long fileId, @Param("status") FileStatus status);

    @Modifying
    @Query("UPDATE FileEntity f SET f.isDeleted = true, f.deletedAt = CURRENT_TIMESTAMP WHERE f.id = :fileId")
    void softDelete(@Param("fileId") Long fileId);

    @Modifying
    @Query("UPDATE FileEntity f SET f.downloadCount = f.downloadCount + 1 WHERE f.id = :fileId")
    void incrementDownloadCount(@Param("fileId") Long fileId);

    @Query("SELECT f FROM FileEntity f WHERE f.ownerId = :ownerId AND f.isDeleted = false ORDER BY f.createdAt DESC")
    Page<FileEntity> findRecentByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT COUNT(f) FROM FileEntity f WHERE f.ownerId = :ownerId AND f.isDeleted = false")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT SUM(f.fileSizeBytes) FROM FileEntity f WHERE f.ownerId = :ownerId AND f.isDeleted = false")
    Long sumStorageUsedByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT SUM(f.fileSizeBytes) FROM FileEntity f WHERE f.organization.id = :orgId AND f.isDeleted = false")
    Long sumStorageUsedByOrganizationId(@Param("orgId") Long organizationId);

    @Query("SELECT f FROM FileEntity f WHERE f.folder.id = :folderId AND f.isDeleted = false ORDER BY f.name ASC")
    List<FileEntity> findByFolderIdAndNotDeleted(@Param("folderId") Long folderId);

    @Query("SELECT f FROM FileEntity f LEFT JOIN FETCH f.folder LEFT JOIN FETCH f.organization WHERE f.id = :id")
    Optional<FileEntity> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT f FROM FileEntity f WHERE f.organization.id = :orgId AND f.isShared = true")
    List<FileEntity> findSharedByOrganizationId(@Param("orgId") Long organizationId);
}
