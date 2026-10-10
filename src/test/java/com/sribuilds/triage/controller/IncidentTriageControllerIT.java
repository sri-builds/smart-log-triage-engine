package com.sribuilds.triage.controller;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import com.sribuilds.triage.dto.TriageRequest;
import com.sribuilds.triage.service.IncidentCacheService;
import com.sribuilds.triage.service.LlmDiagnosis;
import com.sribuilds.triage.service.LlmDiagnosticsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST tests exercising the triage pipeline: cache hit/miss header
 * semantics, LLM orchestration, persistence and payload validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentTriageControllerIT {

    private static final String VALID_PAYLOAD = """
            {
              "serviceName": "BillingBatchProcessor",
              "environment": "PROD",
              "errorLog": "java.lang.NullPointerException: Cannot invoke \\"com.verizon.billing.model.Invoice.getAmount()\\" at com.verizon.billing.BatchJob.execute(BatchJob.java:142)"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentCacheService cacheService;

    @MockitoBean
    private LlmDiagnosticsClient llmDiagnosticsClient;

    private DiagnosticReport report() {
        return DiagnosticReport.builder()
                .incidentId("8d5a9ec2-6f3e-4c7a-9a12-1f2b3c4d5e6f")
                .rootCause("Null invoice returned from payment lookup")
                .severity(Severity.HIGH)
                .recommendedAction(List.of("Guard against null", "Fix the mapping"))
                .suggestedFixSnippet("if (invoice == null) { throw new ...; }")
                .build();
    }

    @Test
    void cacheHitReturnsCachedReportWithTrueHeader() throws Exception {
        DiagnosticReport cached = report();
        cached.setErrorHash("c7b798a2372efcd7538da133c9bed3e3");
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        mockMvc.perform(post("/api/v1/incidents/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "true"))
                .andExpect(jsonPath("$.incidentId").value("8d5a9ec2-6f3e-4c7a-9a12-1f2b3c4d5e6f"))
                .andExpect(jsonPath("$.rootCause").value("Null invoice returned from payment lookup"));

        verify(llmDiagnosticsClient, never()).diagnose(any(TriageRequest.class));
    }

    @Test
    void cacheMissInvokesLlmPersistsAndReturnsFalseHeader() throws Exception {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(llmDiagnosticsClient.diagnose(any(TriageRequest.class)))
                .thenReturn(LlmDiagnosis.healthy(report()));

        mockMvc.perform(post("/api/v1/incidents/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "false"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.recommendedAction", hasSize(2)))
                .andExpect(jsonPath("$.errorHash").isNotEmpty());

        verify(llmDiagnosticsClient).diagnose(any(TriageRequest.class));
        verify(cacheService).put(anyString(), any(DiagnosticReport.class));
    }

    @Test
    void llmFailureReturnsDeterministicFallbackWithFalseHeader() throws Exception {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(llmDiagnosticsClient.diagnose(any(TriageRequest.class)))
                .thenReturn(LlmDiagnosis.degraded(report()));

        mockMvc.perform(post("/api/v1/incidents/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache-Hit", "false"));

        verify(cacheService, never()).put(anyString(), any(DiagnosticReport.class));
    }

    @Test
    void rejectsPayloadMissingServiceName() throws Exception {
        String payload = """
                {
                  "environment": "PROD",
                  "errorLog": "java.lang.NullPointerException at BatchJob.execute(BatchJob.java:142)"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("serviceName")));
    }

    @Test
    void rejectsInvalidEnvironmentValue() throws Exception {
        String payload = """
                {
                  "serviceName": "BillingBatchProcessor",
                  "environment": "uat",
                  "errorLog": "java.lang.NullPointerException at BatchJob.execute(BatchJob.java:142)"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}