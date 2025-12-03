package com.whereq.libra.model;

import com.whereq.libra.dto.SparkJobRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a job in the queue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueuedJob {
    /**
     * Unique job identifier
     */
    private String jobId;

    /**
     * User who submitted the job
     */
    private String userId;

    /**
     * Original job request
     */
    private SparkJobRequest request;

    /**
     * Calculated resource requirements
     */
    private ResourceRequirement resourceRequirement;

    /**
     * Redis Stream record ID (for acknowledgment)
     */
    private String recordId;

    /**
     * When the job was enqueued
     */
    private Instant enqueuedAt;

    /**
     * Retry attempt number
     */
    @Builder.Default
    private int retryCount = 0;
}
