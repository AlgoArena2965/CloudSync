package com.cloudsync.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FileUtils {

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList("mp4", "avi", "mov", "wmv", "flv", "mkv", "webm");
    private static final List<String> AUDIO_EXTENSIONS = Arrays.asList("mp3", "wav", "flac", "aac", "ogg", "wma");
    private static final List<String> DOCUMENT_EXTENSIONS = Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt");

    public static String generateS3Key(String organizationId, String userId, String fileName, String folderPath) {
        String uuid = UUID.randomUUID().toString();
        String sanitizedFileName = sanitizeFileName(fileName);
        String path = folderPath != null && !folderPath.isEmpty() ? folderPath : "root";
        return String.format("org-%s/user-%s/%s/%s/%s", organizationId, userId, path, uuid, sanitizedFileName);
    }

    public static String generateSessionToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateShareToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("_{2,}", "_");
    }

    public static String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    public static String getContentType(String fileName, String mimeType) {
        if (mimeType != null && !mimeType.isEmpty()) {
            return mimeType;
        }
        String extension = getExtension(fileName);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "zip" -> "application/zip";
            case "tar" -> "application/x-tar";
            case "gz" -> "application/gzip";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }

    public static String calculateMD5(InputStream inputStream) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String getFileCategory(String extension) {
        extension = extension != null ? extension.toLowerCase() : "";
        if (IMAGE_EXTENSIONS.contains(extension)) return "IMAGE";
        if (VIDEO_EXTENSIONS.contains(extension)) return "VIDEO";
        if (AUDIO_EXTENSIONS.contains(extension)) return "AUDIO";
        if (DOCUMENT_EXTENSIONS.contains(extension)) return "DOCUMENT";
        return "OTHER";
    }

    public static boolean isImage(String extension) {
        return IMAGE_EXTENSIONS.contains(extension != null ? extension.toLowerCase() : "");
    }

    public static int calculateTotalChunks(long fileSize, long chunkSize) {
        return (int) Math.ceil((double) fileSize / chunkSize);
    }

    public static long calculateChunkSize(long fileSize) {
        if (fileSize <= 5 * 1024 * 1024) return 5 * 1024 * 1024;
        if (fileSize <= 100 * 1024 * 1024) return 10 * 1024 * 1024;
        if (fileSize <= 1024 * 1024 * 1024) return 15 * 1024 * 1024;
        return 20 * 1024 * 1024;
    }
}
