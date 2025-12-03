package com.whereq.libra.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.whereq.libra.model.ExecutionMode;
import com.whereq.libra.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response for job status query
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {
    /**
     * Job identifier
     */
    private String jobId;

    /**
     * Current status
     */
    private JobStatus status;

    /**
     * Execution mode used
     */
    private ExecutionMode executionMode;

    /**
     * Job progress information
     */
    private JobProgress progress;

    /**
     * When the job was submitted
     */
    private Instant submittedAt;

    /**
     * When the job was queued
     */
    private Instant queuedAt;

    /**
     * When the job was scheduled
     */
    private Instant scheduledAt;

    /**
     * When the job started execution
     */
    private Instant startedAt;

    /**
     * When the job completed
     */
    private Instant completedAt;

    /**
     * Resource usage
     */
    private ResourceUsageInfo resourceUsage;

    /**
     * Retry count
     */
    private int retryCount;

    /**
     * Error message (if failed)
     */
    private String errorMessage;

    /**
     * Job result (if succeeded)
     */
    private JsonNode result;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobProgress {
        private int percentage;
        private String currentStage;
        private int tasksCompleted;
        private int tasksTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUsageInfo {
        private int cpuCores;
        private double memoryGB;
    }
}
