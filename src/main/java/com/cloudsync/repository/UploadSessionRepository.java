package com.cloudsync.repository;

import com.cloudsync.model.entity.UploadSession;
import com.cloudsync.model.enums.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {

    Optional<UploadSession> findBySessionToken(String sessionToken);

    List<UploadSession> findByUserId(Long userId);

    List<UploadSession> findByUserIdAndStatus(Long userId, UploadStatus status);

    List<UploadSession> findByStatus(UploadStatus status);

    @Query("SELECT us FROM UploadSession us WHERE us.expiresAt < CURRENT_TIMESTAMP AND us.status = :status")
    List<UploadSession> findExpiredByStatus(@Param("status") UploadStatus status);

    @Modifying
    @Query("UPDATE UploadSession us SET us.status = :status, us.errorMessage = :error WHERE us.id = :sessionId")
    void updateStatusAndError(@Param("sessionId") Long sessionId, @Param("status") UploadStatus status, @Param("error") String error);

    @Modifying
    @Query("DELETE FROM UploadSession us WHERE us.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);

    @Query("SELECT us FROM UploadSession us WHERE us.userId = :userId AND us.status = 'IN_PROGRESS' ORDER BY us.createdAt DESC")
    List<UploadSession> findIncompleteSessionsByUserId(@Param("userId") Long userId);

    boolean existsBySessionToken(String sessionToken);
}
