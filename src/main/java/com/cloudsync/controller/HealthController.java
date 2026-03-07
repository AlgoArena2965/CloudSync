package com.cloudsync.controller;

import com.cloudsync.dto.common.ApiResponse;
import com.cloudsync.partition.NodePartitionService;
import com.cloudsync.resilience.ResilienceService;
import com.cloudsync.s3.S3StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "System health and monitoring endpoints")
public class HealthController {

    private final S3StorageService s3StorageService;
    private final ResilienceService resilienceService;
    private final NodePartitionService nodePartitionService;

    @GetMapping("/status")
    @Operation(summary = "System status", description = "Get detailed system health status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        // Circuit breaker status
        ResilienceService.CircuitBreakerMetrics metrics = s3StorageService.getCircuitBreakerMetrics();
        status.put("s3CircuitBreaker", Map.of(
                "state", metrics.state(),
                "failureRate", metrics.failureRate() + "%",
                "slowCallRate", metrics.slowCallRate() + "%",
                "successfulCalls", metrics.successfulCalls(),
                "failedCalls", metrics.failedCalls(),
                "notPermittedCalls", metrics.notPermittedCalls()
        ));

        // Hash ring status
        NodePartitionService.NodeRingStats ringStats = nodePartitionService.getRingStats();
        status.put("consistentHashRing", Map.of(
                "physicalNodes", ringStats.physicalNodes(),
                "totalVirtualNodes", ringStats.totalVirtualNodes(),
                "virtualNodesPerPhysical", ringStats.virtualNodesPerPhysical()
        ));

        // S3 status
        status.put("s3Service", Map.of(
                "bucket", s3StorageService.getBucketName(),
                "circuitBreakerState", s3StorageService.getCircuitBreakerState().toString()
        ));

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/circuit-breaker")
    @Operation(summary = "Circuit breaker metrics", description = "Get circuit breaker metrics")
    public ResponseEntity<ApiResponse<ResilienceService.CircuitBreakerMetrics>> getCircuitBreakerMetrics() {
        return ResponseEntity.ok(ApiResponse.success(resilienceService.getMetrics()));
    }

    @GetMapping("/partition")
    @Operation(summary = "Partition info", description = "Get consistent hash ring info")
    public ResponseEntity<ApiResponse<NodePartitionService.NodeRingStats>> getPartitionInfo() {
        return ResponseEntity.ok(ApiResponse.success(nodePartitionService.getRingStats()));
    }
}
