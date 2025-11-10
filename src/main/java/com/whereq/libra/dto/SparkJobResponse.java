package com.whereq.libra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Spark job execution response.
 *
 * @author WhereQ Inc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparkJobResponse {
    private String jobId;
    private String sessionId;
    private String state;
    private Object result;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long executionTimeMs;
}
