package com.cloudsync.repository;

import com.cloudsync.model.entity.UploadPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadPartRepository extends JpaRepository<UploadPart, Long> {

    List<UploadPart> findByUploadSessionIdOrderByPartNumber(Long sessionId);

    List<UploadPart> findByUploadSessionIdAndIsUploadedFalse(Long sessionId);

    @Query("SELECT up FROM UploadPart up WHERE up.uploadSession.sessionToken = :sessionToken AND up.isUploaded = false ORDER BY up.partNumber")
    List<UploadPart> findPendingPartsBySessionToken(@Param("sessionToken") String sessionToken);

    Optional<UploadPart> findByUploadSessionIdAndPartNumber(Long sessionId, Integer partNumber);

    @Query("SELECT COUNT(up) FROM UploadPart up WHERE up.uploadSession.id = :sessionId AND up.isUploaded = true")
    int countUploadedPartsBySessionId(@Param("sessionId") Long sessionId);

    void deleteByUploadSessionId(Long sessionId);
}
