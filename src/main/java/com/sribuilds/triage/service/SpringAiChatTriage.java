package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.TriageRequest;
import com.sribuilds.triage.exception.LlmUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Spring AI powered diagnostics provider.
 *
 * <p>Uses the ChatClient structured-output API (backed by {@code BeanOutputConverter})
 * so the model is instructed, via its generated JSON schema, to emit a
 * {@link DiagnosticReport}. Responses are still run through
 * {@link SchemaValidator} before being accepted.</p>
 */
@Slf4j
@Component
@ConditionalOnBean(ChatClient.Builder.class)
@RequiredArgsConstructor
public class SpringAiChatTriage {

    private static final String SYSTEM_PROMPT = """
            You are a senior site-reliability engineer triaging production incidents.
            Analyze the provided stack trace and return a single JSON object that EXACTLY
            matches this schema:
            {
              "incidentId": "string (UUID)",
              "errorHash": "string",
              "rootCause": "string - short explanation of the underlying bug",
              "severity": "CRITICAL | HIGH | MEDIUM | LOW",
              "recommendedAction": ["string", ...] - a NON-EMPTY array of steps,
              "suggestedFixSnippet": "string - optional code fix"
            }
            Rules:
            - severity MUST be exactly one of CRITICAL, HIGH, MEDIUM, LOW.
            - recommendedAction MUST be a non-empty array of strings.
            - "errorHash" MAY be left empty; it is filled server-side.
            - Never emit prose or markdown fences around the JSON.
            """;

    private static final String USER_PROMPT = """
            Service: {serviceName}
            Environment: {environment}

            Raw error log:
            {errorLog}

            Produce the structured diagnostic report now.
            """;

    private final ChatClient.Builder chatClientBuilder;

    /**
     * Invokes the configured chat model and parses a structured report.
     *
     * @param request the incident to diagnose
     * @return the parsed {@link DiagnosticReport}
     * @throws LlmUnavailableException when the provider fails or returns
     *         unparseable content (re-routed to the circuit breaker fallback)
     */
    public DiagnosticReport analyze(TriageRequest request) {
        try {
            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(u -> u.text(USER_PROMPT)
                            .param("serviceName", request.getServiceName())
                            .param("environment", request.getEnvironment())
                            .param("errorLog", request.getErrorLog()))
                    .call()
                    .entity(DiagnosticReport.class);
        } catch (RuntimeException e) {
            log.warn("LLM diagnostics call failed for service={}: {}", request.getServiceName(), e.getMessage());
            throw new LlmUnavailableException("LLM provider call failed: " + e.getMessage(), e);
        }
    }
}