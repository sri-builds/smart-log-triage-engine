package com.sribuilds.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound application-level tuning properties (prefix {@code app.}).
 *
 * @param incident  incident-specific knobs
 * @param llm       LLM fallback message configuration
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Incident incident,
        Llm llm
) {

    /**
     * @param cacheTtl how long deduplicated diagnostics are kept in Redis (default 24h)
     */
    public record Incident(Duration cacheTtl) {
    }

    /**
     * @param fallbackRootCause root cause reported when the LLM is unavailable
     */
    public record Llm(String fallbackRootCause) {
    }
}