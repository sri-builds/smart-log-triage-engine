package com.sribuilds.triage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sribuilds.triage.config.AppProperties;
import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentCacheServiceTest {

    private static final String ERROR_HASH = "5f4dcc3b5aa765d61d8327deb882cf99";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private IncidentCacheService cacheService;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        appProperties = new AppProperties(
                new AppProperties.Incident(Duration.ofHours(24)),
                new AppProperties.Llm("LLM Service Unavailable. Default triage queued."));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new IncidentCacheService(redisTemplate, new ObjectMapper(), appProperties);
    }

    private static String key() {
        return "incident:hash:" + ERROR_HASH;
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(valueOperations.get(key())).thenReturn(null);

        Optional<DiagnosticReport> result = cacheService.get(ERROR_HASH);

        assertThat(result).isEmpty();
    }

    @Test
    void getDeserializesCachedReportOnHit() {
        String cached = """
                {
                  "incidentId": "7ba29f79-394f-4dcf-8b53-e1c0f1f6fb10",
                  "errorHash": "5f4dcc3b5aa765d61d8327deb882cf99",
                  "rootCause": "Null invoice returned from payment lookup",
                  "severity": "HIGH",
                  "recommendedAction": ["Fix null handling", "Add invariant"],
                  "suggestedFixSnippet": "if (payment.getInvoice() == null) throw ..."
                }
                """;
        when(valueOperations.get(key())).thenReturn(cached);

        Optional<DiagnosticReport> result = cacheService.get(ERROR_HASH);

        assertThat(result).isPresent();
        assertThat(result.get().getRootCause()).isEqualTo("Null invoice returned from payment lookup");
        assertThat(result.get().getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(result.get().getRecommendedAction()).hasSize(2);
    }

    @Test
    void getTreatsCorruptCacheEntryAsMiss() {
        when(valueOperations.get(key())).thenReturn("{not-json");

        Optional<DiagnosticReport> result = cacheService.get(ERROR_HASH);

        assertThat(result).isEmpty();
    }

    @Test
    void putSerializesAndStoresWithConfiguredTtl() {
        DiagnosticReport report = DiagnosticReport.builder()
                .incidentId("7ba29f79-394f-4dcf-8b53-e1c0f1f6fb10")
                .errorHash(ERROR_HASH)
                .rootCause("Root cause")
                .severity(Severity.LOW)
                .recommendedAction(List.of("Step 1", "Step 2"))
                .build();

        cacheService.put(ERROR_HASH, report);

        verify(valueOperations).set(
                eq(key()),
                contains("\"rootCause\":\"Root cause\""),
                eq(Duration.ofHours(24)));
    }

    @Test
    void roundTripStoresAndRetrievesReport() {
        DiagnosticReport original = DiagnosticReport.builder()
                .incidentId("7ba29f79-394f-4dcf-8b53-e1c0f1f6fb10")
                .errorHash(ERROR_HASH)
                .rootCause("Root cause")
                .severity(Severity.MEDIUM)
                .recommendedAction(List.of("Step 1", "Step 2"))
                .suggestedFixSnippet("snippet")
                .build();

        when(valueOperations.get(key())).thenAnswer(inv -> writeJson(original));

        cacheService.put(ERROR_HASH, original);
        Optional<DiagnosticReport> result = cacheService.get(ERROR_HASH);

        assertThat(result).isPresent();
        assertThat(result.get().getRootCause()).isEqualTo("Root cause");
        assertThat(result.get().getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.get().getRecommendedAction()).containsExactly("Step 1", "Step 2");
    }

    private String writeJson(DiagnosticReport report) {
        try {
            return new ObjectMapper().writeValueAsString(report);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}