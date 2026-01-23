package com.cloudsync.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "folders", indexes = {
        @Index(name = "idx_folder_parent", columnList = "parent_folder_id"),
        @Index(name = "idx_folder_owner", columnList = "owner_id"),
        @Index(name = "idx_folder_org", columnList = "organization_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Folder extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Folder> subFolders = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FileEntity> files = new ArrayList<>();

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "folder_path", length = 2048)
    private String folderPath;

    @Column(name = "is_shared")
    @Builder.Default
    private Boolean isShared = false;

    @Column(name = "share_token")
    private String shareToken;

    @Column(name = "total_size_bytes")
    @Builder.Default
    private Long totalSizeBytes = 0L;

    @Column(name = "file_count")
    @Builder.Default
    private Integer fileCount = 0;

    public void addFile(FileEntity file) {
        files.add(file);
        file.setFolder(this);
    }

    public void removeFile(FileEntity file) {
        files.remove(file);
        file.setFolder(null);
    }

    public void addSubFolder(Folder sub) {
        subFolders.add(sub);
        sub.setParentFolder(this);
    }
}
