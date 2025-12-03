package com.whereq.libra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whereq.libra.model.JobResult;
import com.whereq.libra.model.JobStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Track job status and metadata in Redis
 */
@Slf4j
@Service
public class JobStatusTracker {

    private static final String STATUS_KEY_PREFIX = "libra:job:status:";
    private static final String METADATA_KEY_PREFIX = "libra:job:metadata:";
    private static final Duration TTL = Duration.ofDays(7); // Keep status for 7 days

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Update job status
     *
     * @param jobId job identifier
     * @param status new status
     * @return Mono that completes when updated
     */
    public Mono<Void> updateStatus(String jobId, JobStatus status) {
        return updateStatus(jobId, status, null);
    }

    /**
     * Update job status with metadata
     *
     * @param jobId job identifier
     * @param status new status
     * @param metadata additional metadata (error message, result, etc.)
     * @return Mono that completes when updated
     */
    public Mono<Void> updateStatus(String jobId, JobStatus status, Object metadata) {
        String statusKey = STATUS_KEY_PREFIX + jobId;
        String metadataKey = METADATA_KEY_PREFIX + jobId;

        Instant now = Instant.now();

        return redisTemplate.opsForValue()
            .set(statusKey, status.name())
            .then(redisTemplate.expire(statusKey, TTL))
            .then(updateMetadata(jobId, status, metadata, now))
            .doOnSuccess(v -> log.info("Job {} status updated: {} → {}",
                jobId, getStatusSync(jobId), status))
            .then();
    }

    /**
     * Update job status with result
     */
    public Mono<Void> updateStatus(String jobId, JobStatus status, JobResult result) {
        return updateStatus(jobId, status, (Object) result);
    }

    /**
     * Get current job status
     *
     * @param jobId job identifier
     * @return Mono with current status
     */
    public Mono<JobStatus> getStatus(String jobId) {
        String statusKey = STATUS_KEY_PREFIX + jobId;

        return redisTemplate.opsForValue()
            .get(statusKey)
            .map(JobStatus::valueOf)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Job not found: " + jobId)));
    }

    /**
     * Get job metadata
     *
     * @param jobId job identifier
     * @return Mono with job metadata
     */
    public Mono<JobMetadata> getMetadata(String jobId) {
        String metadataKey = METADATA_KEY_PREFIX + jobId;

        return redisTemplate.opsForHash()
            .entries(metadataKey)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .map(this::parseMetadata)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Job metadata not found: " + jobId)));
    }

    /**
     * Get retry count for a job
     */
    public Mono<Integer> getRetryCount(String jobId) {
        return getMetadata(jobId)
            .map(JobMetadata::getRetryCount)
            .defaultIfEmpty(0);
    }

    /**
     * Increment retry count
     */
    public Mono<Void> incrementRetryCount(String jobId) {
        return getRetryCount(jobId)
            .flatMap(count -> {
                String metadataKey = METADATA_KEY_PREFIX + jobId;
                return redisTemplate.opsForHash()
                    .put(metadataKey, "retryCount", String.valueOf(count + 1))
                    .doOnSuccess(v -> log.info("Incremented retry count for job {} to {}", jobId, count + 1))
                    .then();
            });
    }

    /**
     * Check if user owns the job
     */
    public Mono<Boolean> isOwner(String jobId, String userId) {
        return getMetadata(jobId)
            .map(metadata -> userId.equals(metadata.getUserId()))
            .defaultIfEmpty(false);
    }

    /**
     * Delete job status and metadata
     */
    public Mono<Void> delete(String jobId) {
        String statusKey = STATUS_KEY_PREFIX + jobId;
        String metadataKey = METADATA_KEY_PREFIX + jobId;

        return redisTemplate.delete(statusKey, metadataKey)
            .doOnSuccess(deleted -> log.info("Deleted job status for {}: {} keys removed", jobId, deleted))
            .then();
    }

    private Mono<Void> updateMetadata(String jobId, JobStatus status, Object metadata, Instant now) {
        String metadataKey = METADATA_KEY_PREFIX + jobId;

        Map<String, String> updates = new HashMap<>();

        // Update timestamps based on status
        switch (status) {
            case SUBMITTED -> updates.put("submittedAt", now.toString());
            case QUEUED -> updates.put("queuedAt", now.toString());
            case SCHEDULED -> updates.put("scheduledAt", now.toString());
            case RUNNING -> updates.put("startedAt", now.toString());
            case SUCCEEDED, FAILED, CANCELLED, TIMEOUT -> updates.put("completedAt", now.toString());
        }

        // Add metadata if provided
        if (metadata != null) {
            try {
                if (metadata instanceof String) {
                    updates.put("errorMessage", (String) metadata);
                } else {
                    updates.put("result", objectMapper.writeValueAsString(metadata));
                }
            } catch (Exception e) {
                log.error("Failed to serialize metadata for job {}", jobId, e);
            }
        }

        if (updates.isEmpty()) {
            return Mono.empty();
        }

        return redisTemplate.opsForHash()
            .putAll(metadataKey, updates)
            .then(redisTemplate.expire(metadataKey, TTL))
            .then();
    }

    private JobMetadata parseMetadata(Map<Object, Object> data) {
        JobMetadata metadata = new JobMetadata();

        data.forEach((key, value) -> {
            String k = (String) key;
            String v = (String) value;

            switch (k) {
                case "userId" -> metadata.setUserId(v);
                case "submittedAt" -> metadata.setSubmittedAt(Instant.parse(v));
                case "queuedAt" -> metadata.setQueuedAt(Instant.parse(v));
                case "scheduledAt" -> metadata.setScheduledAt(Instant.parse(v));
                case "startedAt" -> metadata.setStartedAt(Instant.parse(v));
                case "completedAt" -> metadata.setCompletedAt(Instant.parse(v));
                case "retryCount" -> metadata.setRetryCount(Integer.parseInt(v));
                case "errorMessage" -> metadata.setErrorMessage(v);
                case "result" -> metadata.setResult(v);
            }
        });

        return metadata;
    }

    /**
     * Synchronous status check (for logging only)
     */
    private JobStatus getStatusSync(String jobId) {
        try {
            return getStatus(jobId).block(Duration.ofSeconds(1));
        } catch (Exception e) {
            return JobStatus.SUBMITTED;
        }
    }

    @Data
    public static class JobMetadata {
        private String userId;
        private Instant submittedAt;
        private Instant queuedAt;
        private Instant scheduledAt;
        private Instant startedAt;
        private Instant completedAt;
        private int retryCount;
        private String errorMessage;
        private String result;
    }
}
