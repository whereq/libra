package com.whereq.libra.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of a completed job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResult {
    /**
     * Job identifier
     */
    private String jobId;

    /**
     * Final status
     */
    private JobStatus status;

    /**
     * Text output (stdout/stderr)
     */
    private String output;

    /**
     * Structured result data (for SQL queries, etc.)
     */
    private JsonNode result;

    /**
     * Execution time in seconds
     */
    private long executionTimeSeconds;

    /**
     * Error message if failed
     */
    private String errorMessage;

    /**
     * When the job completed
     */
    private Instant completedAt;

    /**
     * Execution mode used
     */
    private ExecutionMode executionMode;
}
