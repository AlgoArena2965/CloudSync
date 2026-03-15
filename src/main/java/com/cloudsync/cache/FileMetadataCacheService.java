package com.cloudsync.cache;

import com.cloudsync.dto.response.FileResponse;
import com.cloudsync.dto.response.FolderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileMetadataCacheService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String FILE_CACHE = "fileMetadata";
    private static final String FOLDER_CACHE = "folderCache";
    private static final String DASHBOARD_CACHE = "dashboard";
    private static final String SHARE_CACHE = "shareCache";

    public void cacheFileMetadata(Long fileId, FileResponse fileResponse) {
        Cache cache = cacheManager.getCache(FILE_CACHE);
        if (cache != null) {
            cache.put("file:" + fileId, fileResponse);
            cache.put("s3key:" + fileResponse.getS3Key(), fileResponse);
            log.debug("Cached file metadata for id: {}", fileId);
        }
    }

    public Optional<FileResponse> getCachedFileMetadata(Long fileId) {
        Cache cache = cacheManager.getCache(FILE_CACHE);
        if (cache != null) {
            FileResponse cached = cache.get("file:" + fileId, FileResponse.class);
            if (cached != null) {
                log.debug("Cache HIT for file id: {}", fileId);
                return Optional.of(cached);
            }
        }
        log.debug("Cache MISS for file id: {}", fileId);
        return Optional.empty();
    }

    public Optional<FileResponse> getCachedFileByS3Key(String s3Key) {
        Cache cache = cacheManager.getCache(FILE_CACHE);
        if (cache != null) {
            FileResponse cached = cache.get("s3key:" + s3Key, FileResponse.class);
            return Optional.ofNullable(cached);
        }
        return Optional.empty();
    }

    public void evictFileMetadata(Long fileId, String s3Key) {
        Cache cache = cacheManager.getCache(FILE_CACHE);
        if (cache != null) {
            cache.evict("file:" + fileId);
            if (s3Key != null) {
                cache.evict("s3key:" + s3Key);
            }
            log.debug("Evicted file metadata cache for id: {}", fileId);
        }
    }

    public void cacheFolderMetadata(Long folderId, FolderResponse folderResponse) {
        Cache cache = cacheManager.getCache(FOLDER_CACHE);
        if (cache != null) {
            cache.put("folder:" + folderId, folderResponse);
            cache.put("path:" + folderResponse.getFolderPath(), folderResponse);
            log.debug("Cached folder metadata for id: {}", folderId);
        }
    }

    public Optional<FolderResponse> getCachedFolderMetadata(Long folderId) {
        Cache cache = cacheManager.getCache(FOLDER_CACHE);
        if (cache != null) {
            FolderResponse cached = cache.get("folder:" + folderId, FolderResponse.class);
            return Optional.ofNullable(cached);
        }
        return Optional.empty();
    }

    public void evictFolderMetadata(Long folderId, String folderPath) {
        Cache cache = cacheManager.getCache(FOLDER_CACHE);
        if (cache != null) {
            cache.evict("folder:" + folderId);
            if (folderPath != null) {
                cache.evict("path:" + folderPath);
            }
            log.debug("Evicted folder metadata cache for id: {}", folderId);
        }
    }

    public void evictUserCaches(Long userId) {
        try {
            Set<String> keys = redisTemplate.keys("*:" + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted {} cache entries for user: {}", keys.size(), userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict user caches: {}", e.getMessage());
        }
    }

    public void evictOrganizationCaches(Long organizationId) {
        try {
            Set<String> keys = redisTemplate.keys("*:" + organizationId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted {} cache entries for organization: {}", keys.size(), organizationId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict organization caches: {}", e.getMessage());
        }
    }

    public void cacheDashboard(Long userId, Object dashboardData) {
        Cache cache = cacheManager.getCache(DASHBOARD_CACHE);
        if (cache != null) {
            cache.put("user:" + userId, dashboardData);
        }
    }

    public <T> Optional<T> getCachedDashboard(Long userId, Class<T> clazz) {
        Cache cache = cacheManager.getCache(DASHBOARD_CACHE);
        if (cache != null) {
            T cached = cache.get("user:" + userId, clazz);
            return Optional.ofNullable(cached);
        }
        return Optional.empty();
    }

    public void evictDashboard(Long userId) {
        Cache cache = cacheManager.getCache(DASHBOARD_CACHE);
        if (cache != null) {
            cache.evict("user:" + userId);
        }
    }

    public void evictShareCache(String shareToken) {
        Cache cache = cacheManager.getCache(SHARE_CACHE);
        if (cache != null) {
            cache.evict("share:" + shareToken);
        }
    }

    public void evictAllCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
        log.info("All caches cleared");
    }

    public void evictAllFileCaches() {
        Cache cache = cacheManager.getCache(FILE_CACHE);
        if (cache != null) {
            cache.clear();
        }
        log.info("File metadata cache cleared");
    }
}
