package com.sribuilds.triage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound payload for the triage endpoint.
 *
 * <pre>
 * {
 *   "serviceName": "BillingBatchProcessor",
 *   "environment": "PROD",
 *   "errorLog": "java.lang.NullPointerException ..."
 * }
 * </pre>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageRequest {

    @NotBlank(message = "serviceName is required")
    @Size(max = 200, message = "serviceName must not exceed 200 characters")
    private String serviceName;

    @NotBlank(message = "environment is required")
    @Pattern(
            regexp = "PROD|PREPROD|STAGING|QA|DEV|LOCAL",
            message = "environment must be one of PROD, PREPROD, STAGING, QA, DEV or LOCAL"
    )
    private String environment;

    @NotBlank(message = "errorLog is required")
    @Size(min = 10, max = 40000, message = "errorLog must be between 10 and 40000 characters")
    private String errorLog;
}