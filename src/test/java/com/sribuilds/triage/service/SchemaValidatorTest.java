package com.sribuilds.triage.service;

import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.dto.Severity;
import com.sribuilds.triage.exception.SchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValidatorTest {

    private SchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SchemaValidator();
    }

    private DiagnosticReport validReport() {
        return DiagnosticReport.builder()
                .incidentId("8d5a9ec2-6f3e-4c7a-9a12-1f2b3c4d5e6f")
                .errorHash("5f4dcc3b5aa765d61d8327deb882cf99")
                .rootCause("Null pointer on payment invoice lookup")
                .severity(Severity.HIGH)
                .recommendedAction(List.of("Guard against null", "Add invariant"))
                .suggestedFixSnippet("if (invoice == null) { ... }")
                .build();
    }

    @Test
    void acceptsConformingReport() {
        assertThatCode(() -> validator.validate(validReport())).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullReport() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("payload is missing");
    }

    @Test
    void rejectsBlankRootCause() {
        DiagnosticReport report = validReport();
        report.setRootCause("   ");

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("rootCause");
    }

    @Test
    void rejectsNullSeverity() {
        DiagnosticReport report = validReport();
        report.setSeverity(null);

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void rejectsEmptyRecommendedAction() {
        DiagnosticReport report = validReport();
        report.setRecommendedAction(null);

        assertThatThrownBy(() -> validator.validate(report))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("recommendedAction");
    }

    @Test
    void acceptsMissingOptionalFixSnippet() {
        DiagnosticReport report = validReport();
        report.setSuggestedFixSnippet(null);

        assertThatCode(() -> validator.validate(report)).doesNotThrowAnyException();
    }
}