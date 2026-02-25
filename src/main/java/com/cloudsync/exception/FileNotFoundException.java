package com.cloudsync.exception;

public class FileNotFoundException extends ResourceNotFoundException {
    public FileNotFoundException(String message) {
        super(message);
    }

    public FileNotFoundException(Long fileId) {
        super("File not found with id: " + fileId);
    }
}
