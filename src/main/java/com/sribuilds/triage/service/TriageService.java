package com.sribuilds.triage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.TriageRequest;
import com.sribuilds.triage.model.IncidentReportEntity;
import com.sribuilds.triage.repository.IncidentReportRepository;
import com.sribuilds.triage.util.Md5HashingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the triage pipeline:
 *
 * <ol>
 *     <li>compute the MD5 dedup hash of the raw error log</li>
 *     <li>short-circuit on a Redis cache hit and return the cached report</li>
 *     <li>on a miss, call the resilient LLM diagnostics client</li>
 *     <li>persist healthy results to PostgreSQL and warm the Redis cache</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageService {

    private final IncidentCacheService cacheService;
    private final LlmDiagnosticsClient llmDiagnosticsClient;
    private final IncidentReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    /**
     * Triages a raw incident payload.
     *
     * @param request validated inbound payload
     * @return the diagnostic report plus whether it came from the cache
     */
    public TriageOutcome triage(TriageRequest request) {
        String errorHash = Md5HashingUtil.hash(request.getErrorLog());

        Optional<DiagnosticReport> cached = cacheService.get(errorHash);
        if (cached.isPresent()) {
            log.info("Triage cache HIT for errorHash={}", errorHash);
            return new TriageOutcome(cached.get(), true);
        }

        log.info("Triage cache MISS for errorHash={}; invoking LLM diagnostics", errorHash);
        LlmDiagnosis diagnosis = llmDiagnosticsClient.diagnose(request);
        DiagnosticReport report = normalize(request, errorHash, diagnosis);

        if (diagnosis.degraded()) {
            log.warn("LLM provider degraded; returning fallback WITHOUT caching errorHash={}", errorHash);
        } else {
            persist(request, report);
            cacheService.put(errorHash, report);
        }

        return new TriageOutcome(report, false);
    }

    /**
     * Normalizes the LLM response to the canonical report shape: authoritative
     * MD5 error hash plus a server-generated incident id when absent.
     */
    private DiagnosticReport normalize(TriageRequest request, String errorHash, LlmDiagnosis diagnosis) {
        DiagnosticReport report = diagnosis.report();
        report.setErrorHash(errorHash);
        if (report.getIncidentId() == null || report.getIncidentId().isBlank()) {
            report.setIncidentId(UUID.randomUUID().toString());
        }
        return report;
    }

    /**
     * Persists the report as the durable system-of-record row for debugging and
     * post-incident analysis.
     */
    private void persist(TriageRequest request, DiagnosticReport report) {
        IncidentReportEntity entity = IncidentReportEntity.builder()
                .incidentId(report.getIncidentId())
                .errorHash(report.getErrorHash())
                .serviceName(request.getServiceName())
                .environment(request.getEnvironment())
                .rootCause(report.getRootCause())
                .severity(report.getSeverity() != null ? report.getSeverity().name() : null)
                .recommendedAction(writeJson(report.getRecommendedAction()))
                .suggestedFixSnippet(report.getSuggestedFixSnippet())
                .build();
        reportRepository.save(entity);
    }

    private String writeJson(List<String> values) {
        if (values == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize recommended actions; storing empty list", e);
            return "[]";
        }
    }
}