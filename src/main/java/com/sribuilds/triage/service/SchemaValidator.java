package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.exception.SchemaValidationException;
import org.springframework.stereotype.Component;

/**
 * Enforces the strict diagnostic report output schema. Rejects LLM payloads
 * that omit required fields or carry structurally-invalid collections.
 */
@Component
public class SchemaValidator {

    /**
     * Validates that a report conforms to the required output JSON schema.
     *
     * @param report the report produced by the LLM provider
     * @throws SchemaValidationException when the payload is non-conforming
     */
    public void validate(DiagnosticReport report) {
        if (report == null) {
            throw new SchemaValidationException("llmResponse: payload is missing");
        }
        requireText(report.getRootCause(), "rootCause");
        if (report.getSeverity() == null) {
            throw new SchemaValidationException("severity: must be one of CRITICAL, HIGH, MEDIUM, LOW");
        }
        if (report.getRecommendedAction() == null || report.getRecommendedAction().isEmpty()) {
            throw new SchemaValidationException("recommendedAction: must be a non-empty array of strings");
        }
        if (report.getRecommendedAction().size() > 20) {
            throw new SchemaValidationException("recommendedAction: must not exceed 20 steps");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SchemaValidationException(field + ": is required and must not be blank");
        }
    }
}