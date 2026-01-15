package com.cloudsync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "cloudsync")
@Component
@Data
public class CloudSyncProperties {

    private UploadConfig upload = new UploadConfig();
    private StorageConfig storage = new StorageConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private ConsistentHashConfig consistentHash = new ConsistentHashConfig();

    @Data
    public static class UploadConfig {
        private String tempDirectory = "/tmp/cloudsync-uploads";
        private boolean resumable = true;
        private int cleanupInterval = 3600;
    }

    @Data
    public static class StorageConfig {
        private long maxFileSize = 10L * 1024 * 1024 * 1024; // 10GB
        private String allowedExtensions = "jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,zip,tar,gz,mp4,mp3,wav,avi,mov";
    }

    @Data
    public static class RateLimitConfig {
        private int uploadPerMinute = 100;
        private int downloadPerMinute = 200;
        private int apiPerMinute = 1000;
    }

    @Data
    public static class ConsistentHashConfig {
        private int virtualNodes = 150;
        private String nodes = "node1,node2,node3";
    }
}
