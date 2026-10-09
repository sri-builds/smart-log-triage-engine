package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.TriageRequest;

/**
 * Boundary between the triage orchestration and the LLM diagnostics provider.
 *
 * <p>Implementations are expected to produce a {@link DiagnosticReport} whose
 * shape conforms to the enforced output JSON schema.</p>
 */
public interface LlmDiagnosticsClient {

    /**
     * Generates a structured diagnostic report for a raw error log.
     *
     * @param request the ingested incident payload
     * @return the diagnosis, including whether the provider was degraded
     */
    LlmDiagnosis diagnose(TriageRequest request);
}