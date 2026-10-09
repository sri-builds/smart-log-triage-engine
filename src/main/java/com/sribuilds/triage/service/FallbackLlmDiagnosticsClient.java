package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.TriageRequest;
import com.sribuilds.triage.util.Md5HashingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TEMPORARY placeholder diagnostics client used until the Spring AI powered
 * provider is wired up. Always returns the deterministic fallback so the
 * pipeline remains fully runnable end-to-end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackLlmDiagnosticsClient implements LlmDiagnosticsClient {

    private final FallbackDiagnosticsProvider fallbackProvider;

    @Override
    public LlmDiagnosis diagnose(TriageRequest request) {
        log.warn("Using placeholder LLM diagnostics client for service={}", request.getServiceName());
        String errorHash = Md5HashingUtil.hash(request.getErrorLog());
        return LlmDiagnosis.degraded(fallbackProvider.fallback(request, errorHash));
    }
}