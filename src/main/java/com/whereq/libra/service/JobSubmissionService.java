package com.whereq.libra.service;

import com.whereq.libra.dto.AsyncJobSubmitResponse;
import com.whereq.libra.dto.JobCancellationResponse;
import com.whereq.libra.dto.SparkJobRequest;
import com.whereq.libra.exception.QuotaExceededException;
import com.whereq.libra.model.JobStatus;
import com.whereq.libra.model.QueuedJob;
import com.whereq.libra.model.ResourceRequirement;
import com.whereq.libra.queue.JobQueue;
import com.whereq.libra.resource.ResourceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for job submission and management
 */
@Slf4j
@Service
public class JobSubmissionService {

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private JobStatusTracker statusTracker;

    @Autowired
    private ResourceCalculator resourceCalculator;

    @Autowired
    private AdmissionController admissionController;

    /**
     * Submit a job for async execution
     *
     * @param request job request
     * @param userId user identifier
     * @return Mono with submission response
     */
    public Mono<AsyncJobSubmitResponse> submitJob(SparkJobRequest request, String userId) {
        String jobId = generateJobId();

        return Mono.just(jobId)
            // 1. Mark as submitted
            .flatMap(id -> statusTracker.updateStatus(id, JobStatus.SUBMITTED)
                .thenReturn(id))

            // 2. Validate (basic validation)
            .flatMap(id -> validateJob(request)
                .thenReturn(id)
                .onErrorResume(e -> {
                    statusTracker.updateStatus(id, JobStatus.FAILED, e.getMessage()).subscribe();
                    return Mono.error(e);
                }))

            // 3. Calculate resources
            .map(id -> {
                ResourceRequirement req = resourceCalculator.calculate(request);
                return QueuedJob.builder()
                    .jobId(id)
                    .userId(userId)
                    .request(request)
                    .resourceRequirement(req)
                    .enqueuedAt(Instant.now())
                    .retryCount(0)
                    .build();
            })

            // 4. Admission control
            .flatMap(job -> admissionController.admitJob(job)
                .flatMap(decision -> {
                    switch (decision) {
                        case ADMIT, QUEUE:
                            return enqueueJob(job);
                        case REJECT:
                            return Mono.error(new QuotaExceededException(
                                "Queue is full, cannot accept more jobs"));
                    }
                    return Mono.empty();
                }))

            // 5. Return response
            .map(job -> AsyncJobSubmitResponse.builder()
                .jobId(job.getJobId())
                .status(JobStatus.QUEUED)
                .submittedAt(Instant.now())
                .build())

            .doOnSuccess(response -> log.info("Job {} submitted successfully by user {}",
                response.getJobId(), userId))
            .doOnError(e -> log.error("Job submission failed for user {}: {}", userId, e.getMessage()));
    }

    /**
     * Cancel a running or queued job
     *
     * @param jobId job identifier
     * @param userId user identifier
     * @return Mono with cancellation response
     */
    public Mono<JobCancellationResponse> cancelJob(String jobId, String userId) {
        return statusTracker.isOwner(jobId, userId)
            .flatMap(isOwner -> {
                if (!isOwner) {
                    return Mono.error(new IllegalArgumentException(
                        "Not authorized to cancel this job"));
                }

                return statusTracker.getStatus(jobId)
                    .flatMap(currentStatus -> {
                        if (currentStatus.isTerminal()) {
                            return Mono.error(new IllegalStateException(
                                "Cannot cancel job in terminal status: " + currentStatus));
                        }

                        // Update status to CANCELLED
                        return statusTracker.updateStatus(jobId, JobStatus.CANCELLED)
                            .then(Mono.defer(() -> {
                                // Remove from queue if queued
                                if (currentStatus == JobStatus.QUEUED) {
                                    return jobQueue.remove(jobId);
                                }
                                // TODO: Kill executor if running
                                return Mono.empty();
                            }))
                            .thenReturn(JobCancellationResponse.builder()
                                .jobId(jobId)
                                .status(JobStatus.CANCELLED)
                                .cancelledAt(Instant.now())
                                .message("Job cancelled successfully")
                                .build());
                    });
            })
            .doOnSuccess(response -> log.info("Job {} cancelled by user {}", jobId, userId))
            .doOnError(e -> log.error("Failed to cancel job {}: {}", jobId, e.getMessage()));
    }

    /**
     * Enqueue job for execution
     */
    private Mono<QueuedJob> enqueueJob(QueuedJob job) {
        return statusTracker.updateStatus(job.getJobId(), JobStatus.QUEUED)
            .then(jobQueue.enqueue(job))
            .thenReturn(job);
    }

    /**
     * Validate job request
     */
    private Mono<Void> validateJob(SparkJobRequest request) {
        return Mono.fromRunnable(() -> {
            if (request.getCode() == null && request.getFilePath() == null) {
                throw new IllegalArgumentException(
                    "Either 'code' or 'filePath' must be specified");
            }

            if ("jar".equals(request.getKind()) || "jar-class".equals(request.getKind())) {
                if (request.getMainClass() == null) {
                    throw new IllegalArgumentException(
                        "Main class is required for JAR execution");
                }
                if (request.getFilePath() == null) {
                    throw new IllegalArgumentException(
                        "File path is required for JAR execution");
                }
            }
        });
    }

    /**
     * Generate unique job ID
     */
    private String generateJobId() {
        return "job-" + UUID.randomUUID().toString();
    }
}
