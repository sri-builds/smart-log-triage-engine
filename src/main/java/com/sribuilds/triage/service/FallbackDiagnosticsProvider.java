package com.sribuilds.triage.service;

import com.sribuilds.triage.config.AppProperties;
import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import com.sribuilds.triage.dto.TriageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Produces the deterministic static fallback report used whenever the LLM
 * provider fails (circuit open, rate limited, timeout or 5xx).
 */
@Component
@RequiredArgsConstructor
public class FallbackDiagnosticsProvider {

    private final AppProperties appProperties;

    /**
     * Builds the fallback report for a given request and error hash.
     */
    public DiagnosticReport fallback(TriageRequest request, String errorHash) {
        return DiagnosticReport.builder()
                .incidentId(UUID.randomUUID().toString())
                .errorHash(errorHash)
                .rootCause(appProperties.llm().fallbackRootCause())
                .severity(Severity.MEDIUM)
                .recommendedAction(List.of(
                        "Retry triage once the LLM endpoint is reachable.",
                        "Clear the circuit/rate limiter state if the outage is resolved.",
                        "Trace the incident manually for immediate remediation."
                ))
                .build();
    }
}