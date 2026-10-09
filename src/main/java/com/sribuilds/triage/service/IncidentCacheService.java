package com.sribuilds.triage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sribuilds.triage.config.AppProperties;
import com.sribuilds.triage.config.RedisConfig;
import com.sribuilds.triage.dto.DiagnosticReport;
import com.sribuilds.triage.exception.CacheSerializationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Redis-backed deduplication cache.
 *
 * <p>Keys follow {@code incident:hash:{md5}} and expire after a configurable
 * TTL (default 24 hours). A hit short-circuits the expensive LLM evaluation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    /**
     * Resolves a cached diagnostic report for the given error hash, if present.
     *
     * @param errorHash MD5 digest of the raw error log
     * @return the cached report, or empty on a cache miss
     */
    public Optional<DiagnosticReport> get(String errorHash) {
        String key = RedisConfig.incidentKey(errorHash);
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            log.debug("Cache MISS for key={}", key);
            return Optional.empty();
        }
        try {
            DiagnosticReport report = objectMapper.readValue(raw, DiagnosticReport.class);
            log.debug("Cache HIT for key={}", key);
            return Optional.of(report);
        } catch (JsonProcessingException e) {
            log.warn("Corrupt cache entry for key={}, treating as miss", key, e);
            return Optional.empty();
        }
    }

    /**
     * Stores a diagnostic report under the given error hash with the configured TTL.
     */
    public void put(String errorHash, DiagnosticReport report) {
        String key = RedisConfig.incidentKey(errorHash);
        try {
            String raw = objectMapper.writeValueAsString(report);
            redisTemplate.opsForValue().set(key, raw, appProperties.incident().cacheTtl());
            log.debug("Cached report for key={} with TTL={}", key, appProperties.incident().cacheTtl());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize report for key={}", key, e);
            throw new CacheSerializationException("Failed to serialize diagnostic report", e);
        }
    }
}