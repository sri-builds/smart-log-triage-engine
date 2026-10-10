package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import com.sribuilds.triage.dto.TriageRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientLlmDiagnosticsClientTest {

    private static final String FALLBACK_CAUSE = "LLM Service Unavailable. Default triage queued.";

    @Mock
    private ObjectProvider<SpringAiChatTriage> chatTriageProvider;
    @Mock
    private SpringAiChatTriage chatTriage;
    @Mock
    private FallbackDiagnosticsProvider fallbackProvider;

    private TriageRequest request() {
        return TriageRequest.builder()
                .serviceName("BillingBatchProcessor")
                .environment("PROD")
                .errorLog("java.lang.NullPointerException at com.verizon.billing.BatchJob.execute(BatchJob.java:142)")
                .build();
    }

    private DiagnosticReport fallbackReport() {
        return DiagnosticReport.builder()
                .incidentId("8d5a9ec2-6f3e-4c7a-9a12-1f2b3c4d5e6f")
                .errorHash("placeholder-hash")
                .rootCause(FALLBACK_CAUSE)
                .severity(Severity.MEDIUM)
                .recommendedAction(List.of("Retry triage"))
                .build();
    }

    private ResilientLlmDiagnosticsClient client(CircuitBreakerRegistry cbRegistry, RateLimiterRegistry rlRegistry) {
        return new ResilientLlmDiagnosticsClient(chatTriageProvider, cbRegistry, rlRegistry, fallbackProvider);
    }

    @Test
    void openCircuitShortCircuitsToDeterministicFallback() {
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        cbRegistry.circuitBreaker(ResilientLlmDiagnosticsClient.LLM_BACKEND).transitionToOpenState();
        when(fallbackProvider.fallback(any(), anyString())).thenReturn(fallbackReport());

        LlmDiagnosis result = client(cbRegistry, RateLimiterRegistry.ofDefaults()).diagnose(request());

        assertThat(result.degraded()).isTrue();
        assertThat(result.report().getRootCause()).isEqualTo(FALLBACK_CAUSE);
        assertThat(result.report().getSeverity()).isEqualTo(Severity.MEDIUM);
        verifyNoInteractions(chatTriageProvider);
    }

    @Test
    void delegateFailureIsRecordedAndFallsBack() {
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
        when(chatTriageProvider.getIfAvailable()).thenReturn(chatTriage);
        doThrow(new RuntimeException("connection refused")).when(chatTriage).analyze(any());
        when(fallbackProvider.fallback(any(), anyString())).thenReturn(fallbackReport());

        LlmDiagnosis result = client(cbRegistry, rlRegistry).diagnose(request());

        assertThat(result.degraded()).isTrue();
        CircuitBreaker breaker = cbRegistry.circuitBreaker(ResilientLlmDiagnosticsClient.LLM_BACKEND);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
    }

    @Test
    void healthyResponseIsReturnedWithoutDegradation() {
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        when(chatTriageProvider.getIfAvailable()).thenReturn(chatTriage);
        DiagnosticReport healthy = fallbackReport();
        healthy.setRootCause("Null invoice returned from payment lookup");
        healthy.setSeverity(Severity.HIGH);
        when(chatTriage.analyze(any())).thenReturn(healthy);

        LlmDiagnosis result = client(cbRegistry, RateLimiterRegistry.ofDefaults()).diagnose(request());

        assertThat(result.degraded()).isFalse();
        assertThat(result.report().getRootCause()).isEqualTo("Null invoice returned from payment lookup");
        assertThat(cbRegistry.circuitBreaker(ResilientLlmDiagnosticsClient.LLM_BACKEND)
                .getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void healthCheckTraversesHalfOpenAfterCooldown() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .permittedNumberOfCallsInHalfOpenState(1)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50)
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(config);
        CircuitBreaker breaker = cbRegistry.circuitBreaker(ResilientLlmDiagnosticsClient.LLM_BACKEND);
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();

        when(chatTriageProvider.getIfAvailable()).thenReturn(chatTriage);
        DiagnosticReport healthy = fallbackReport();
        healthy.setRootCause("Recovered");
        healthy.setSeverity(Severity.LOW);
        when(chatTriage.analyze(any())).thenReturn(healthy);

        LlmDiagnosis result = client(cbRegistry, RateLimiterRegistry.ofDefaults()).diagnose(request());

        assertThat(result.degraded()).isFalse();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}