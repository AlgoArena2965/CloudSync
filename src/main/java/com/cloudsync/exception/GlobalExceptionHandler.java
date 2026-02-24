package com.cloudsync.exception;

import com.cloudsync.dto.common.ApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileNotFound(FileNotFoundException ex, HttpServletRequest request) {
        log.warn("File not found: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials: {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>error("Invalid email or password").withPath(request.getRequestURI()));
    }

    @ExceptionHandler(com.cloudsync.exception.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(com.cloudsync.exception.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(StorageQuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageQuotaExceeded(StorageQuotaExceededException ex, HttpServletRequest request) {
        log.warn("Storage quota exceeded: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(UploadSessionExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSessionExpired(UploadSessionExpiredException ex, HttpServletRequest request) {
        log.warn("Upload session expired: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Duplicate resource: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitBreakerOpen(CallNotPermittedException ex, HttpServletRequest request) {
        log.error("Circuit breaker open: {} - {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>error("Service temporarily unavailable. Please try again later.")
                        .withPath(request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Max upload size exceeded: {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.<Void>error("File size exceeds the maximum allowed limit").withPath(request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation errors: {} - {}", request.getRequestURI(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>error("Validation failed", errors).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(CloudSyncException.class)
    public ResponseEntity<ApiResponse<Void>> handleCloudSyncException(CloudSyncException ex, HttpServletRequest request) {
        log.error("CloudSync error: {} - {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error(ex.getMessage()).withPath(request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {} - {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error("An unexpected error occurred. Please try again later.")
                        .withPath(request.getRequestURI()));
    }
}
