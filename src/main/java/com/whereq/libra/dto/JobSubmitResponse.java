package com.whereq.libra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for job submission.
 *
 * @author WhereQ Inc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from job submission")
public class JobSubmitResponse {

    @Schema(description = "Job execution status", example = "SUCCESS")
    private JobStatus status;

    @Schema(description = "Job output/logs")
    private String output;

    @Schema(description = "Error message if job failed")
    private String errorMessage;

    @Schema(description = "Execution time in milliseconds", example = "5432")
    private Long executionTimeMs;

    public enum JobStatus {
        SUCCESS,
        FAILED,
        RUNNING
    }
}
