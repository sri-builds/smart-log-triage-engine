package com.sribuilds.triage.service;

import com.sribuilds.triage.config.AppProperties;
import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import com.sribuilds.triage.dto.TriageRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackDiagnosticsProviderTest {

    private static final String FALLBACK_CAUSE = "LLM Service Unavailable. Default triage queued.";

    @Test
    void buildsDeterministicStaticReport() {
        AppProperties props = new AppProperties(
                new AppProperties.Incident(Duration.ofHours(24)),
                new AppProperties.Llm(FALLBACK_CAUSE));
        TriageRequest request = TriageRequest.builder()
                .serviceName("BillingBatchProcessor")
                .environment("PROD")
                .errorLog("java.lang.NullPointerException at com.verizon.billing.BatchJob.execute(BatchJob.java:142)")
                .build();
        String errorHash = com.sribuilds.triage.util.Md5HashingUtil.hash(request.getErrorLog());

        DiagnosticReport report = new FallbackDiagnosticsProvider(props).fallback(request, errorHash);

        assertThat(report.getErrorHash()).isEqualTo(errorHash);
        assertThat(report.getRootCause()).isEqualTo(FALLBACK_CAUSE);
        assertThat(report.getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(report.getRecommendedAction()).isNotEmpty();
        assertThat(report.getIncidentId()).isNotBlank();
    }
}