package com.whereq.libra.controller;

import com.whereq.libra.dto.AsyncJobSubmitResponse;
import com.whereq.libra.dto.JobCancellationResponse;
import com.whereq.libra.dto.JobStatusResponse;
import com.whereq.libra.dto.SparkJobRequest;
import com.whereq.libra.exception.QuotaExceededException;
import com.whereq.libra.model.JobStatus;
import com.whereq.libra.service.JobSubmissionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Controller for async Spark job submission and management
 * Implements Phase 1: Async Foundation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/spark/jobs")
public class AsyncSparkJobController {

    @Autowired
    private JobSubmissionService jobSubmissionService;

    /**
     * Submit a Spark job for async execution
     *
     * @param request job request
     * @param authHeader authorization header
     * @return Mono with 202 Accepted response
     */
    @PostMapping("/submit")
    public Mono<ResponseEntity<AsyncJobSubmitResponse>> submitJob(
            @Valid @RequestBody SparkJobRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);

        log.info("Received async job submission from user {}: kind={}, executionMode={}",
            userId, request.getKind(), request.getExecutionMode());

        return jobSubmissionService.submitJob(request, userId)
            .map(response -> ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/spark/jobs/" + response.getJobId()))
                .body(response))
            .onErrorResume(IllegalArgumentException.class, e -> {
                log.error("Validation error: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .badRequest()
                    .body(AsyncJobSubmitResponse.error(e.getMessage())));
            })
            .onErrorResume(QuotaExceededException.class, e -> {
                log.error("Quota exceeded: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(AsyncJobSubmitResponse.error(e.getMessage())));
            })
            .onErrorResume(Exception.class, e -> {
                log.error("Unexpected error during job submission", e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AsyncJobSubmitResponse.error("Internal server error: " + e.getMessage())));
            });
    }

    /**
     * Get job status
     * TODO: Implement in Phase 2
     *
     * @param jobId job identifier
     * @param authHeader authorization header
     * @return Mono with job status
     */
    @GetMapping("/{jobId}")
    public Mono<ResponseEntity<JobStatusResponse>> getJobStatus(
            @PathVariable String jobId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);

        log.debug("Job status request for {} from user {} - NOT IMPLEMENTED", jobId, userId);

        // Phase 1: Not implemented yet, will be added in Phase 2
        return Mono.just(ResponseEntity
            .status(HttpStatus.NOT_IMPLEMENTED)
            .build());
    }

    /**
     * Cancel a job
     *
     * @param jobId job identifier
     * @param authHeader authorization header
     * @return Mono with cancellation response
     */
    @DeleteMapping("/{jobId}")
    public Mono<ResponseEntity<JobCancellationResponse>> cancelJob(
            @PathVariable String jobId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String userId = extractUserId(authHeader);

        log.info("Job cancellation request for {} from user {}", jobId, userId);

        return jobSubmissionService.cancelJob(jobId, userId)
            .map(ResponseEntity::ok)
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
            .onErrorResume(IllegalArgumentException.class, e -> {
                log.error("Authorization error: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .build());
            })
            .onErrorResume(IllegalStateException.class, e -> {
                log.error("Invalid state for cancellation: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build());
            })
            .onErrorResume(Exception.class, e -> {
                log.error("Error cancelling job {}", jobId, e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
            });
    }

    /**
     * Extract user ID from authorization header
     * TODO: Implement proper authentication/authorization in Phase 2
     */
    private String extractUserId(String authHeader) {
        // Placeholder implementation
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return "user-from-token";
        }
        return "anonymous";
    }
}
