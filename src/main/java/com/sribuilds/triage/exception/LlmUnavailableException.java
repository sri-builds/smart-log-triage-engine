package com.sribuilds.triage.exception;

/**
 * Thrown when the LLM provider is unavailable, unresponsive or returned an
 * unusable payload. The Resilience4j fallback intercepts this and downgrades
 * to the deterministic static triage report.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}