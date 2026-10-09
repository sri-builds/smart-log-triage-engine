package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;

/**
 * Result of a triage execution, carrying the report and the cache disposition.
 *
 * @param report   the diagnostic report returned to the caller
 * @param cacheHit {@code true} when the report was served from the Redis cache
 */
public record TriageOutcome(DiagnosticReport report, boolean cacheHit) {
}