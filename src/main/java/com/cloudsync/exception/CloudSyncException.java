package com.cloudsync.exception;

public class CloudSyncException extends RuntimeException {
    public CloudSyncException(String message) {
        super(message);
    }

    public CloudSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
