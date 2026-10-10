package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.TriageRequest;
import com.sribuilds.triage.util.Md5HashingUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Fault-tolerant {@link LlmDiagnosticsClient} implementation.
 *
 * <p>Every LLM call is decorated with a Resilience4j {@link CircuitBreaker} and
 * {@link RateLimiter} (instance {@code llmTriage}). When the provider exceeds
 * configured limits, times out, or returns 5xx, the call is short-circuited and
 * a deterministic static fallback report is returned instead.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientLlmDiagnosticsClient implements LlmDiagnosticsClient {

    /** Matches the circuit breaker + rate limiter instance configured in application.yml. */
    public static final String LLM_BACKEND = "llmTriage";

    private final ObjectProvider<SpringAiChatTriage> chatTriageProvider;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final FallbackDiagnosticsProvider fallbackProvider;

    @Override
    public LlmDiagnosis diagnose(TriageRequest request) {
        String errorHash = Md5HashingUtil.hash(request.getErrorLog());
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(LLM_BACKEND);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(LLM_BACKEND);

        AtomicBoolean degraded = new AtomicBoolean(false);

        Supplier<DiagnosticReport> guarded = Decorators.ofSupplier(() -> invokeDelegate(request))
                .withCircuitBreaker(circuitBreaker)
                .withRateLimiter(rateLimiter)
                .withFallback(
                        List.of(CallNotPermittedException.class, RequestNotPermitted.class, RuntimeException.class),
                        throwable -> {
                            if (!(throwable instanceof CallNotPermittedException)
                                    && !(throwable instanceof RequestNotPermitted)) {
                                log.warn("LLM diagnostics degraded: {}", throwable.getMessage());
                            } else {
                                log.warn("LLM diagnostics short-circuited: {}", throwable.getClass().getSimpleName());
                            }
                            degraded.set(true);
                            return fallbackProvider.fallback(request, errorHash);
                        })
                .decorate();

        DiagnosticReport report = guarded.get();
        return degraded.get() ? LlmDiagnosis.degraded(report) : LlmDiagnosis.healthy(report);
    }

    private DiagnosticReport invokeDelegate(TriageRequest request) {
        SpringAiChatTriage chatTriage = chatTriageProvider.getIfAvailable();
        if (chatTriage == null) {
            throw new IllegalStateException("No LLM chat provider configured (spring.ai.model.chat=none)");
        }
        return chatTriage.analyze(request);
    }
}