package com.cloudsync.service;

import com.cloudsync.model.entity.AuditLog;
import com.cloudsync.repository.AuditLogRepository;
import com.cloudsync.util.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(Long userId, Long organizationId, String action, String entityType,
                          Long entityId, String entityName, String oldValue, String newValue) {
        try {
            RequestContext ctx = RequestContext.getCurrent();

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(ctx != null ? ctx.getIpAddress() : null)
                    .userAgent(ctx != null ? ctx.getUserAgent() : null)
                    .requestPath(ctx != null ? ctx.getRequestPath() : null)
                    .requestMethod(ctx != null ? ctx.getRequestMethod() : null)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log audit action: {} - {}", action, e.getMessage());
        }
    }

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(Long userId, Long organizationId, String action, String entityType,
                          Long entityId, String entityName, String additionalData) {
        try {
            RequestContext ctx = RequestContext.getCurrent();

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .additionalData(additionalData)
                    .ipAddress(ctx != null ? ctx.getIpAddress() : null)
                    .userAgent(ctx != null ? ctx.getUserAgent() : null)
                    .requestPath(ctx != null ? ctx.getRequestPath() : null)
                    .requestMethod(ctx != null ? ctx.getRequestMethod() : null)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log audit action: {} - {}", action, e.getMessage());
        }
    }

    @Transactional
    public void logResponseStatus(Long userId, String action, Integer statusCode) {
        try {
            RequestContext ctx = RequestContext.getCurrent();
            if (ctx == null) return;

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .responseStatus(statusCode)
                    .ipAddress(ctx.getIpAddress())
                    .userAgent(ctx.getUserAgent())
                    .requestPath(ctx.getRequestPath())
                    .requestMethod(ctx.getRequestMethod())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log response status: {}", e.getMessage());
        }
    }
}
