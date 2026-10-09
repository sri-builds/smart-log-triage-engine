package com.sribuilds.triage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis infrastructure configuration.
 *
 * <p>The deduplication cache stores serialized {@code DiagnosticReport} JSON
 * documents under {@code incident:hash:{md5}} keys with a 24-hour TTL.</p>
 */
@Configuration
public class RedisConfig {

    /** Redis key namespace for deduplicated incident reports. */
    public static final String INCIDENT_HASH_PREFIX = "incident:hash:";

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setEnableTransactionSupport(false);
        return template;
    }

    /** Builds the Redis key for a given MD5 hash. */
    public static String incidentKey(String errorHash) {
        return INCIDENT_HASH_PREFIX + errorHash;
    }
}