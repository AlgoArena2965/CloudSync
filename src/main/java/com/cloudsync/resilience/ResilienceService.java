package com.cloudsync.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Service
@Slf4j
public class ResilienceService {

    private final CircuitBreaker s3CircuitBreaker;
    private final Retry s3Retry;
    private final TimeLimiter s3TimeLimiter;

    public ResilienceService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.s3CircuitBreaker = circuitBreakerRegistry.circuitBreaker("s3Operations");
        this.s3Retry = retryRegistry.retry("s3Operations");
        this.s3TimeLimiter = timeLimiterRegistry.timeLimiter("s3Operations");

        setupEventListeners();
    }

    private void setupEventListeners() {
        s3CircuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("S3 CircuitBreaker state transition: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onFailureRateExceeded(event ->
                        log.error("S3 CircuitBreaker failure rate exceeded: {}% (threshold: {}%)",
                                event.getFailureRate(), 50))
                .onSlowCallRateExceeded(event ->
                        log.warn("S3 CircuitBreaker slow call rate exceeded: {}% (threshold: {}%)",
                                event.getSlowCallRate(), 80));

        s3Retry.getEventPublisher()
                .onRetry(event ->
                        log.warn("S3 Retry attempt #{} for error: {}",
                                event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()))
                .onError(event ->
                        log.error("S3 Retry exhausted after {} attempts. Final error: {}",
                                event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
    }

    /**
     * Execute a supplier with circuit breaker and retry protection.
     */
    public <T> T executeWithResilience(Supplier<T> supplier) {
        @SuppressWarnings("unchecked")
        Supplier<T> retriedSupplier = (Supplier<T>) s3Retry.executeSupplier(supplier);
        @SuppressWarnings("unchecked")
        T result = (T) s3CircuitBreaker.executeSupplier(retriedSupplier);
        return result;
    }

    /**
     * Execute with fallback on circuit breaker open.
     */
    @SuppressWarnings("unchecked")
    public <T> T executeWithFallback(Supplier<T> supplier, Supplier<T> fallback) {
        try {
            Supplier<T> retriedSupplier = (Supplier<T>) s3Retry.executeSupplier(supplier);
            return (T) s3CircuitBreaker.executeSupplier(retriedSupplier);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN. Executing fallback.");
            return fallback.get();
        }
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return s3CircuitBreaker.getState();
    }

    public CircuitBreakerMetrics getMetrics() {
        return new CircuitBreakerMetrics(
                s3CircuitBreaker.getState().toString(),
                s3CircuitBreaker.getMetrics().getFailureRate(),
                s3CircuitBreaker.getMetrics().getSlowCallRate(),
                s3CircuitBreaker.getMetrics().getNumberOfSuccessfulCalls(),
                s3CircuitBreaker.getMetrics().getNumberOfFailedCalls(),
                s3CircuitBreaker.getMetrics().getNumberOfNotPermittedCalls()
        );
    }

    public record CircuitBreakerMetrics(
            String state,
            float failureRate,
            float slowCallRate,
            long successfulCalls,
            long failedCalls,
            long notPermittedCalls
    ) {}
}
