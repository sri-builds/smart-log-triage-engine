package com.sribuilds.triage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import com.sribuilds.triage.dto.TriageRequest;
import com.sribuilds.triage.repository.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriageServiceTest {

    private static final String ERROR_LOG =
            "java.lang.NullPointerException: Cannot invoke \"com.verizon.billing.model.Invoice.getAmount()\" "
                    + "at com.verizon.billing.BatchJob.execute(BatchJob.java:142)";

    private IncidentCacheService cacheService;
    private LlmDiagnosticsClient llmDiagnosticsClient;
    private IncidentReportRepository reportRepository;
    private ObjectMapper objectMapper;
    private TriageService triageService;

    @BeforeEach
    void setUp() {
        cacheService = mock(IncidentCacheService.class);
        llmDiagnosticsClient = mock(LlmDiagnosticsClient.class);
        reportRepository = mock(IncidentReportRepository.class);
        objectMapper = new ObjectMapper();
        triageService = new TriageService(cacheService, llmDiagnosticsClient, reportRepository, objectMapper);
    }

    private TriageRequest request() {
        return TriageRequest.builder()
                .serviceName("BillingBatchProcessor")
                .environment("PROD")
                .errorLog(ERROR_LOG)
                .build();
    }

    private DiagnosticReport report() {
        return DiagnosticReport.builder()
                .incidentId("8d5a9ec2-6f3e-4c7a-9a12-1f2b3c4d5e6f")
                .errorHash("d41d8cd98f00b204e9800998ecf8427e")
                .rootCause("Invoice object is null before getAmount() invocation")
                .severity(Severity.HIGH)
                .recommendedAction(List.of("Guard against null", "Fix the mapping"))
                .suggestedFixSnippet("if (invoice == null) { ... }")
                .build();
    }

    @Test
    void returnsCachedReportWithoutInvokingLlmOnCacheHit() {
        DiagnosticReport cached = report();
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        TriageOutcome outcome = triageService.triage(request());

        assertThat(outcome.cacheHit()).isTrue();
        assertThat(outcome.report()).isSameAs(cached);
        verify(llmDiagnosticsClient, never()).diagnose(any(TriageRequest.class));
        verify(reportRepository, never()).save(any());
    }

    @Test
    void invokesLlmAndPersistsAndCachesOnCacheMiss() {
        DiagnosticReport llmReport = report();
        llmReport.setErrorHash(null);
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(llmDiagnosticsClient.diagnose(any(TriageRequest.class)))
                .thenReturn(LlmDiagnosis.healthy(llmReport));

        TriageOutcome outcome = triageService.triage(request());

        assertThat(outcome.cacheHit()).isFalse();
        assertThat(outcome.report().getErrorHash()).isEqualTo(com.sribuilds.triage.util.Md5HashingUtil.hash(ERROR_LOG));
        verify(llmDiagnosticsClient).diagnose(any(TriageRequest.class));
        verify(reportRepository).save(any());
        verify(cacheService).put(eq(com.sribuilds.triage.util.Md5HashingUtil.hash(ERROR_LOG)), any(DiagnosticReport.class));
    }

    @Test
    void doesNotPersistOrCacheDegradedFallback() {
        DiagnosticReport fallback = report();
        fallback.setRootCause("LLM Service Unavailable. Default triage queued.");
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(llmDiagnosticsClient.diagnose(any(TriageRequest.class)))
                .thenReturn(LlmDiagnosis.degraded(fallback));

        TriageOutcome outcome = triageService.triage(request());

        assertThat(outcome.cacheHit()).isFalse();
        assertThat(outcome.report().getRootCause()).isEqualTo("LLM Service Unavailable. Default triage queued.");
        verify(reportRepository, never()).save(any());
        verify(cacheService, never()).put(anyString(), any(DiagnosticReport.class));
    }

    @Test
    void assignsServerSideIncidentIdWhenLlmOmitsIt() {
        DiagnosticReport llmReport = report();
        llmReport.setIncidentId(null);
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(llmDiagnosticsClient.diagnose(any(TriageRequest.class)))
                .thenReturn(LlmDiagnosis.healthy(llmReport));

        TriageOutcome outcome = triageService.triage(request());

        assertThat(outcome.report().getIncidentId()).isNotBlank();
    }
}