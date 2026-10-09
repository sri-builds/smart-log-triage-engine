package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;

/**
 * Outcome of an LLM triage attempt.
 *
 * @param report   the structured diagnostic report
 * @param degraded {@code true} when the provider was unavailable and the
 *                 deterministic fallback was returned instead
 */
public record LlmDiagnosis(DiagnosticReport report, boolean degraded) {

    public static LlmDiagnosis healthy(DiagnosticReport report) {
        return new LlmDiagnosis(report, false);
    }

    public static LlmDiagnosis degraded(DiagnosticReport report) {
        return new LlmDiagnosis(report, true);
    }
}