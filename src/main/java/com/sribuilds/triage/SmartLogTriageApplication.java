package com.sribuilds.triage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Smart Incident &amp; Log Diagnostics Engine.
 *
 * <p>Ingests raw stack traces, deduplicates them via MD5 hashing backed by Redis,
 * and produces structured root-cause diagnostics using an LLM guarded by
 * Resilience4j circuit breakers and rate limiters.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartLogTriageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLogTriageApplication.class, args);
    }
}
