-- Make organization_id nullable in files table to support standalone users without organizations
ALTER TABLE files MODIFY COLUMN organization_id BIGINT NULL;
