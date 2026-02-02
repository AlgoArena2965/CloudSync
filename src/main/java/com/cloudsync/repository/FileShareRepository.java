package com.cloudsync.repository;

import com.cloudsync.model.entity.FileShare;
import com.cloudsync.model.enums.ShareType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, Long> {

    Optional<FileShare> findByShareToken(String shareToken);

    List<FileShare> findByFileId(Long fileId);

    List<FileShare> findBySharedByUserId(Long userId);

    List<FileShare> findBySharedWithId(Long userId);

    List<FileShare> findBySharedWithIdAndIsActiveTrue(Long userId);

    @Query("SELECT fs FROM FileShare fs WHERE fs.file.id = :fileId AND fs.isActive = true AND (fs.expiresAt IS NULL OR fs.expiresAt > CURRENT_TIMESTAMP)")
    List<FileShare> findActiveSharesByFileId(@Param("fileId") Long fileId);

    @Query("SELECT fs FROM FileShare fs WHERE fs.isActive = true AND fs.expiresAt IS NOT NULL AND fs.expiresAt < CURRENT_TIMESTAMP")
    List<FileShare> findExpiredShares();

    @Modifying
    @Query("UPDATE FileShare fs SET fs.isActive = false WHERE fs.expiresAt < CURRENT_TIMESTAMP")
    int deactivateExpiredShares();

    @Modifying
    @Query("UPDATE FileShare fs SET fs.currentDownloads = fs.currentDownloads + 1 WHERE fs.id = :shareId")
    void incrementDownloadCount(@Param("shareId") Long shareId);

    @Modifying
    @Query("UPDATE FileShare fs SET fs.viewCount = fs.viewCount + 1 WHERE fs.id = :shareId")
    void incrementViewCount(@Param("shareId") Long shareId);

    @Query("SELECT COUNT(fs) FROM FileShare fs WHERE fs.sharedByUserId = :userId AND fs.isActive = true")
    long countActiveSharesByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(fs) FROM FileShare fs WHERE fs.sharedByUserId = :userId")
    long countSharesByUserId(@Param("userId") Long userId);

    boolean existsByShareToken(String shareToken);

    @Query("SELECT fs FROM FileShare fs WHERE fs.file.id = :fileId AND fs.sharedWith.id = :userId AND fs.isActive = true")
    Optional<FileShare> findActiveShareForUserAndFile(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
