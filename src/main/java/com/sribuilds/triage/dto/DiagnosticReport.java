package com.sribuilds.triage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Structured diagnostic report produced by the triage engine.
 *
 * <p>Field names (and the array shape of {@code recommendedAction}) constitute
 * the enforced output JSON schema that the LLM response must conform to.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticReport {

    /** Server/LLM generated unique incident identifier. */
    private String incidentId;

    /** MD5 hash of the raw error log (deduplication key). */
    private String errorHash;

    /** Short explanation of the underlying bug. */
    private String rootCause;

    /** Assigned severity bucket. */
    private Severity severity;

    /** Ordered remediation steps. */
    private List<String> recommendedAction;

    /** Optional code-level fix snippet. */
    private String suggestedFixSnippet;
}