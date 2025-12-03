package com.whereq.libra.dto;

import com.whereq.libra.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response for async job submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJobSubmitResponse {
    /**
     * Unique job identifier
     */
    private String jobId;

    /**
     * Current job status
     */
    private JobStatus status;

    /**
     * When the job was submitted
     */
    private Instant submittedAt;

    /**
     * Estimated start time (if queued)
     */
    private Instant estimatedStartTime;

    /**
     * Error message (if submission failed)
     */
    private String errorMessage;

    /**
     * Create error response
     */
    public static AsyncJobSubmitResponse error(String message) {
        return AsyncJobSubmitResponse.builder()
            .status(JobStatus.FAILED)
            .errorMessage(message)
            .submittedAt(Instant.now())
            .build();
    }
}
