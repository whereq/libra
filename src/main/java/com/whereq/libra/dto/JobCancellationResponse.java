package com.whereq.libra.dto;

import com.whereq.libra.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response for job cancellation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobCancellationResponse {
    /**
     * Job identifier
     */
    private String jobId;

    /**
     * Status after cancellation (should be CANCELLED)
     */
    private JobStatus status;

    /**
     * When the job was cancelled
     */
    private Instant cancelledAt;

    /**
     * Cancellation message
     */
    private String message;
}
