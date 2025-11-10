package com.whereq.libra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for WhereQ Libra.
 *
 * @author WhereQ Inc.
 */
@Configuration
@ConfigurationProperties(prefix = "libra")
@Data
public class LibraProperties {

    private SessionConfig session = new SessionConfig();

    @Data
    public static class SessionConfig {
        /**
         * Session management mode.
         * SHARED: Single SparkSession shared across all requests (default)
         * ISOLATED: One SparkSession per user/session ID
         */
        private SessionMode mode = SessionMode.SHARED;

        /**
         * Maximum number of concurrent sessions in ISOLATED mode.
         */
        private int maxSessions = 10;

        /**
         * Session timeout in minutes.
         */
        private int timeoutMinutes = 30;

        /**
         * Session pooling configuration.
         */
        private PoolingConfig pooling = new PoolingConfig();
    }

    @Data
    public static class PoolingConfig {
        /**
         * Enable session pooling (pre-create sessions for faster response).
         */
        private boolean enabled = false;

        /**
         * Minimum number of sessions in pool.
         */
        private int minSize = 2;

        /**
         * Maximum number of sessions in pool.
         */
        private int maxSize = 10;
    }

    public enum SessionMode {
        /**
         * Single shared SparkSession for all requests.
         * Better performance, less isolation.
         */
        SHARED,

        /**
         * One SparkSession per session ID.
         * Better isolation, more overhead.
         */
        ISOLATED
    }
}
