package com.cloudsync.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @Test
    @DisplayName("Should generate valid S3 key")
    void testGenerateS3Key() {
        String key = FileUtils.generateS3Key("org1", "user1", "document.pdf", "work/reports");
        assertNotNull(key);
        assertTrue(key.contains("org-org1"));
        assertTrue(key.contains("user-user1"));
        assertTrue(key.contains("document.pdf"));
        assertTrue(key.startsWith("org-org1/user-user1/"));
    }

    @Test
    @DisplayName("Should generate unique session tokens")
    void testGenerateSessionToken() {
        String token1 = FileUtils.generateSessionToken();
        String token2 = FileUtils.generateSessionToken();
        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
        assertEquals(32, token1.length()); // UUID without dashes
    }

    @Test
    @DisplayName("Should sanitize filenames")
    void testSanitizeFileName() {
        assertEquals("file_name.pdf", FileUtils.sanitizeFileName("file#name.pdf"));
        assertEquals("document_final_v2_.docx", FileUtils.sanitizeFileName("document_final (v2).docx"));
        assertEquals("unnamed", FileUtils.sanitizeFileName(""));
        assertEquals("unnamed", FileUtils.sanitizeFileName(null));
    }

    @Test
    @DisplayName("Should extract extension correctly")
    void testGetExtension() {
        assertEquals("pdf", FileUtils.getExtension("document.pdf"));
        assertEquals("docx", FileUtils.getExtension("report.DOCX"));
        assertEquals("", FileUtils.getExtension("noextension"));
        assertEquals("", FileUtils.getExtension(null));
    }

    @Test
    @DisplayName("Should determine content type")
    void testGetContentType() {
        assertEquals("image/jpeg", FileUtils.getContentType("photo.jpg", null));
        assertEquals("application/pdf", FileUtils.getContentType("doc.pdf", null));
        assertEquals("video/mp4", FileUtils.getContentType("video.mp4", null));
        assertEquals("application/msword", FileUtils.getContentType("doc.doc", null));
        assertEquals("application/octet-stream", FileUtils.getContentType("unknown.xyz", null));
    }

    @Test
    @DisplayName("Should calculate total chunks correctly")
    void testCalculateTotalChunks() {
        assertEquals(1, FileUtils.calculateTotalChunks(5 * 1024 * 1024, 5 * 1024 * 1024));
        assertEquals(2, FileUtils.calculateTotalChunks(10 * 1024 * 1024, 5 * 1024 * 1024));
        assertEquals(4, FileUtils.calculateTotalChunks(15 * 1024 * 1024 + 1, 5 * 1024 * 1024));
        assertEquals(10, FileUtils.calculateTotalChunks(100 * 1024 * 1024, 10 * 1024 * 1024));
    }

    @Test
    @DisplayName("Should calculate chunk size based on file size")
    void testCalculateChunkSize() {
        // Small file (< 5MB)
        assertEquals(5 * 1024 * 1024, FileUtils.calculateChunkSize(3 * 1024 * 1024));

        // Medium file (5-100MB)
        assertEquals(10 * 1024 * 1024, FileUtils.calculateChunkSize(50 * 1024 * 1024));

        // Large file (100MB-1GB)
        assertEquals(15 * 1024 * 1024, FileUtils.calculateChunkSize(500 * 1024 * 1024));

        // Very large file (>1GB)
        assertEquals(20 * 1024 * 1024, FileUtils.calculateChunkSize(5L * 1024 * 1024 * 1024));
    }

    @Test
    @DisplayName("Should format file sizes correctly")
    void testFormatFileSize() {
        assertEquals("1.00 KB", FileUtils.formatFileSize(1024));
        assertEquals("1.00 KB", FileUtils.formatFileSize(1025));
        assertEquals("1.50 KB", FileUtils.formatFileSize(1536));
        assertEquals("1.00 MB", FileUtils.formatFileSize(1024 * 1024));
        assertEquals("1.00 GB", FileUtils.formatFileSize(1024L * 1024 * 1024));
    }

    @Test
    @DisplayName("Should categorize files correctly")
    void testGetFileCategory() {
        assertEquals("IMAGE", FileUtils.getFileCategory("jpg"));
        assertEquals("VIDEO", FileUtils.getFileCategory("mp4"));
        assertEquals("AUDIO", FileUtils.getFileCategory("mp3"));
        assertEquals("DOCUMENT", FileUtils.getFileCategory("pdf"));
        assertEquals("OTHER", FileUtils.getFileCategory("xyz"));
    }

    @Test
    @DisplayName("Should identify images correctly")
    void testIsImage() {
        assertTrue(FileUtils.isImage("jpg"));
        assertTrue(FileUtils.isImage("PNG"));
        assertFalse(FileUtils.isImage("pdf"));
        assertFalse(FileUtils.isImage(null));
    }

    @Test
    @DisplayName("Should generate unique share tokens")
    void testGenerateShareToken() {
        String token1 = FileUtils.generateShareToken();
        String token2 = FileUtils.generateShareToken();
        assertNotEquals(token1, token2);
        assertEquals(48, token1.length()); // 32 + 16
    }
}
