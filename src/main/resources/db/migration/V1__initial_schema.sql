-- =====================================================
-- CloudSync Database Schema
-- MySQL 8.x
-- =====================================================

-- Organizations table
CREATE TABLE IF NOT EXISTS organizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(100) UNIQUE,
    description TEXT,
    storage_quota_bytes BIGINT DEFAULT 5368709120,
    storage_used_bytes BIGINT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    max_users INT DEFAULT 50,
    owner_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    INDEX idx_org_status (status),
    INDEX idx_org_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER',
    is_enabled BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    storage_used_bytes BIGINT DEFAULT 0,
    refresh_token TEXT,
    refresh_token_expiry DATETIME,
    organization_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE SET NULL,
    INDEX idx_user_email (email),
    INDEX idx_user_org (organization_id),
    INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add foreign key for organization owner after users table exists
ALTER TABLE organizations ADD CONSTRAINT fk_org_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL;

-- Folders table
CREATE TABLE IF NOT EXISTS folders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_folder_id BIGINT,
    owner_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    folder_path VARCHAR(2048),
    is_shared BOOLEAN DEFAULT FALSE,
    share_token VARCHAR(100),
    total_size_bytes BIGINT DEFAULT 0,
    file_count INT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    FOREIGN KEY (parent_folder_id) REFERENCES folders(id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    INDEX idx_folder_parent (parent_folder_id),
    INDEX idx_folder_owner (owner_id),
    INDEX idx_folder_org (organization_id),
    INDEX idx_folder_path (folder_path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Files table
CREATE TABLE IF NOT EXISTS files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    mime_type VARCHAR(100),
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    storage_size_bytes BIGINT,
    s3_key VARCHAR(512) NOT NULL UNIQUE,
    s3_bucket VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    folder_id BIGINT,
    owner_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    file_hash VARCHAR(64),
    content_type VARCHAR(100),
    extension VARCHAR(20),
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at DATETIME,
    is_shared BOOLEAN DEFAULT FALSE,
    share_token VARCHAR(100),
    download_count BIGINT DEFAULT 0,
    last_accessed_at DATETIME,
    version INT DEFAULT 1,
    previous_version_key VARCHAR(512),
    owner BIGINT,
    node_partition VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE SET NULL,
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
    FOREIGN KEY (owner) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_file_folder (folder_id),
    INDEX idx_file_owner (owner_id),
    INDEX idx_file_org (organization_id),
    INDEX idx_file_s3_key (s3_key),
    INDEX idx_file_name (name),
    INDEX idx_file_deleted (is_deleted),
    INDEX idx_file_shared (is_shared)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- File shares table
CREATE TABLE IF NOT EXISTS file_shares (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    share_token VARCHAR(100) NOT NULL UNIQUE,
    share_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    shared_by_user_id BIGINT NOT NULL,
    shared_with_user_id BIGINT,
    share_password VARCHAR(255),
    max_downloads INT,
    current_downloads INT DEFAULT 0,
    expires_at DATETIME,
    allow_preview BOOLEAN DEFAULT TRUE,
    allow_edit BOOLEAN DEFAULT FALSE,
    view_count BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
    FOREIGN KEY (shared_with_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_share_token (share_token),
    INDEX idx_share_file (file_id),
    INDEX idx_share_expiry (expires_at),
    INDEX idx_share_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Upload sessions table
CREATE TABLE IF NOT EXISTS upload_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token VARCHAR(100) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100),
    content_type VARCHAR(100),
    chunk_size_bytes BIGINT,
    total_chunks INT,
    uploaded_chunks INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    user_id BIGINT NOT NULL,
    organization_id BIGINT,
    folder_id BIGINT,
    destination_s3_key VARCHAR(512),
    upload_id VARCHAR(255),
    parts_info TEXT,
    file_hash VARCHAR(64),
    temp_file_path VARCHAR(512),
    error_message TEXT,
    retry_count INT DEFAULT 0,
    expires_at DATETIME NOT NULL,
    last_chunk_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    INDEX idx_upload_session_token (session_token),
    INDEX idx_upload_user (user_id),
    INDEX idx_upload_status (status),
    INDEX idx_upload_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Upload parts table
CREATE TABLE IF NOT EXISTS upload_parts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_session_id BIGINT NOT NULL,
    part_number INT NOT NULL,
    etag VARCHAR(100),
    part_size_bytes BIGINT,
    byte_start BIGINT,
    byte_end BIGINT,
    is_uploaded BOOLEAN DEFAULT FALSE,
    uploaded_at DATETIME,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    temp_chunk_path VARCHAR(512),
    checksum VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    FOREIGN KEY (upload_session_id) REFERENCES upload_sessions(id) ON DELETE CASCADE,
    INDEX idx_part_session (upload_session_id),
    INDEX idx_part_number (part_number),
    INDEX idx_part_uploaded (is_uploaded),
    UNIQUE KEY uk_session_part (upload_session_id, part_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Audit logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    organization_id BIGINT,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    entity_name VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    response_status INT,
    error_message TEXT,
    old_value TEXT,
    new_value TEXT,
    additional_data TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_timestamp (created_at),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add foreign key for folders owner
ALTER TABLE folders ADD CONSTRAINT fk_folder_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;
