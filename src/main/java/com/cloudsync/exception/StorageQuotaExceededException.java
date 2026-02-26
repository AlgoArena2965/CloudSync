package com.cloudsync.exception;

public class StorageQuotaExceededException extends CloudSyncException {
    public StorageQuotaExceededException(String message) {
        super(message);
    }
}
