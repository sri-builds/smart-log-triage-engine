package com.sribuilds.triage.exception;

/**
 * Thrown when an LLM response does not conform to the enforced diagnostic
 * report JSON schema (missing required fields, empty action list, etc.).
 * Mapped to {@code 422 UNPROCESSABLE ENTITY}.
 */
public class SchemaValidationException extends RuntimeException {

    public SchemaValidationException(String message) {
        super(message);
    }
}