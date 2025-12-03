package com.whereq.libra.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Retry policy for failed jobs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryPolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Maximum number of retry attempts
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Initial backoff interval in milliseconds
     */
    @Builder.Default
    private long initialIntervalMs = 1000;

    /**
     * Backoff multiplier
     */
    @Builder.Default
    private int backoffMultiplier = 2;

    /**
     * Maximum backoff interval in milliseconds
     */
    @Builder.Default
    private long maxIntervalMs = 60000;

    /**
     * Get default retry policy
     */
    public static RetryPolicy defaultPolicy() {
        return RetryPolicy.builder().build();
    }
}
