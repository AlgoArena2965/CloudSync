package com.cloudsync.repository;

import com.cloudsync.model.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByOwnerId(Long ownerId);

    List<Folder> findByOwnerIdAndParentFolderIsNull(Long ownerId);

    List<Folder> findByOwnerIdAndParentFolderId(Long ownerId, Long parentFolderId);

    Optional<Folder> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("SELECT f FROM Folder f LEFT JOIN FETCH f.organization WHERE f.id = :id")
    Optional<Folder> findByIdWithOrganization(@Param("id") Long id);

    @Query("SELECT f FROM Folder f LEFT JOIN FETCH f.parentFolder WHERE f.id = :id")
    Optional<Folder> findByIdWithParent(@Param("id") Long id);

    @Query("SELECT f FROM Folder f WHERE f.ownerId = :ownerId AND f.name = :name AND f.parentFolder.id = :parentId")
    Optional<Folder> findByOwnerIdAndNameAndParentId(@Param("ownerId") Long ownerId, @Param("name") String name, @Param("parentId") Long parentId);

    @Query("SELECT COUNT(f) FROM Folder f WHERE f.ownerId = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT f FROM Folder f WHERE f.ownerId = :ownerId ORDER BY f.createdAt DESC")
    List<Folder> findRecentByOwnerId(@Param("ownerId") Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    @Query("SELECT f FROM Folder f WHERE f.folderPath LIKE %:path% AND f.ownerId = :ownerId")
    List<Folder> findAllDescendants(@Param("path") String path, @Param("ownerId") Long ownerId);
}
