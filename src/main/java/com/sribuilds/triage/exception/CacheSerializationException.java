package com.sribuilds.triage.exception;

/**
 * Thrown when a cached diagnostic report cannot be serialized/deserialized.
 */
public class CacheSerializationException extends RuntimeException {

    public CacheSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}