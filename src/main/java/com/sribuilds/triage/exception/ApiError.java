package com.sribuilds.triage.exception;

import lombok.Builder;

import java.time.Instant;

/**
 * Consistent, machine-readable error envelope returned by the API.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP status reason phrase
 * @param message   human-readable error detail
 * @param path      request path that triggered the error
 */
@Builder
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {
}