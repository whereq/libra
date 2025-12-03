package com.whereq.libra.service;

import com.whereq.libra.executor.JobExecutor;
import com.whereq.libra.model.JobResult;
import com.whereq.libra.model.JobStatus;
import com.whereq.libra.model.QueuedJob;
import com.whereq.libra.model.RetryPolicy;
import com.whereq.libra.queue.JobQueue;
import com.whereq.libra.resource.ResourceMonitor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Background job processor that consumes from queue and executes jobs
 */
@Slf4j
@Service
public class AsyncJobExecutor {

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private JobStatusTracker statusTracker;

    @Autowired
    private ExecutionStrategyFactory executionStrategyFactory;

    @Autowired
    private ResourceMonitor resourceMonitor;

    @Autowired
    private WebhookNotifier webhookNotifier;

    @Autowired
    private MeterRegistry meterRegistry;

    private Counter successCounter;
    private Counter failureCounter;
    private Timer executionTimer;

    @PostConstruct
    public void initialize() {
        // Register metrics
        successCounter = Counter.builder("libra.jobs.succeeded")
            .description("Number of successfully completed jobs")
            .register(meterRegistry);

        failureCounter = Counter.builder("libra.jobs.failed")
            .description("Number of failed jobs")
            .register(meterRegistry);

        executionTimer = Timer.builder("libra.jobs.execution.time")
            .description("Job execution time")
            .register(meterRegistry);
    }

    @PostConstruct
    public void startJobProcessor() {
        log.info("Starting async job processor");

        jobQueue.consumeAsFlux()
            // Only process jobs if resources are available
            .filter(job -> {
                boolean canRun = resourceMonitor.canAccommodate(job.getResourceRequirement());
                if (!canRun) {
                    log.debug("Delaying job {} - resources unavailable", job.getJobId());
                }
                return canRun;
            })
            // Execute job with error handling
            .flatMap(job -> executeJob(job)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> handleSuccess(job, result))
                .doOnError(error -> handleError(job, error))
                .onErrorResume(e -> {
                    log.error("Error processing job {}: {}", job.getJobId(), e.getMessage(), e);
                    return Mono.empty(); // Continue processing next job
                })
            )
            .doOnError(e -> log.error("Fatal error in job processor", e))
            .retry() // Restart processor on fatal error
            .subscribe();

        log.info("Async job processor started successfully");
    }

    /**
     * Execute a single job
     */
    private Mono<JobResult> executeJob(QueuedJob job) {
        return Mono.fromCallable(() -> {
            log.info("Starting execution of job {}", job.getJobId());

            // Update status to SCHEDULED
            statusTracker.updateStatus(job.getJobId(), JobStatus.SCHEDULED).subscribe();

            // Reserve resources
            resourceMonitor.reserveResources(job.getJobId(), job.getResourceRequirement());

            // Select appropriate executor
            JobExecutor executor = executionStrategyFactory.selectExecutor(job.getRequest());

            // Update status to RUNNING
            statusTracker.updateStatus(job.getJobId(), JobStatus.RUNNING).subscribe();

            // Execute job (blocking operation)
            long startTime = System.currentTimeMillis();
            JobResult result = executor.executeJob(job);
            long executionTime = System.currentTimeMillis() - startTime;

            // Record metrics
            executionTimer.record(Duration.ofMillis(executionTime));

            log.info("Job {} completed in {}ms", job.getJobId(), executionTime);

            return result;
        })
        .timeout(Duration.ofMinutes(getTimeout(job)))
        .doFinally(signal -> {
            // Always release resources
            resourceMonitor.releaseResources(job.getJobId());
        });
    }

    /**
     * Handle successful job completion
     */
    private void handleSuccess(QueuedJob job, JobResult result) {
        log.info("Job {} completed successfully", job.getJobId());

        // Update status
        statusTracker.updateStatus(job.getJobId(), JobStatus.SUCCEEDED, result)
            .subscribe();

        // Increment success counter
        successCounter.increment();

        // Notify webhooks
        if (job.getRequest().getNotifications() != null &&
            job.getRequest().getNotifications().shouldNotify(JobStatus.SUCCEEDED)) {

            webhookNotifier.notify(
                job.getRequest().getNotifications().getWebhook(),
                job.getJobId(),
                JobStatus.SUCCEEDED,
                result
            ).subscribe();
        }

        // Acknowledge queue (remove from queue)
        jobQueue.acknowledge(job.getJobId()).subscribe();
    }

    /**
     * Handle job failure with retry logic
     */
    private void handleError(QueuedJob job, Throwable error) {
        log.error("Job {} failed: {}", job.getJobId(), error.getMessage(), error);

        RetryPolicy retryPolicy = job.getRequest().getRetryPolicy() != null
            ? job.getRequest().getRetryPolicy()
            : RetryPolicy.defaultPolicy();

        statusTracker.getRetryCount(job.getJobId())
            .flatMap(retryCount -> {
                if (retryCount < retryPolicy.getMaxRetries()) {
                    // Retry with exponential backoff
                    log.info("Retrying job {} (attempt {}/{})",
                        job.getJobId(), retryCount + 1, retryPolicy.getMaxRetries());

                    return statusTracker.updateStatus(job.getJobId(), JobStatus.RETRYING)
                        .then(statusTracker.incrementRetryCount(job.getJobId()))
                        .then(Mono.delay(Duration.ofMillis(calculateBackoff(retryCount, retryPolicy))))
                        .flatMap(x -> {
                            // Update retry count in queued job
                            job.setRetryCount(retryCount + 1);

                            // Re-enqueue
                            return statusTracker.updateStatus(job.getJobId(), JobStatus.QUEUED)
                                .then(jobQueue.enqueue(job));
                        });
                } else {
                    // Max retries exceeded
                    log.warn("Job {} failed permanently after {} retries",
                        job.getJobId(), retryPolicy.getMaxRetries());

                    failureCounter.increment();

                    return statusTracker.updateStatus(
                            job.getJobId(),
                            JobStatus.FAILED,
                            error.getMessage()
                        )
                        .then(notifyFailure(job, error))
                        .then(jobQueue.acknowledge(job.getJobId()));
                }
            })
            .subscribe();
    }

    /**
     * Notify webhooks about failure
     */
    private Mono<Void> notifyFailure(QueuedJob job, Throwable error) {
        if (job.getRequest().getNotifications() != null &&
            job.getRequest().getNotifications().shouldNotify(JobStatus.FAILED)) {

            return webhookNotifier.notify(
                job.getRequest().getNotifications().getWebhook(),
                job.getJobId(),
                JobStatus.FAILED,
                error
            );
        }
        return Mono.empty();
    }

    /**
     * Calculate exponential backoff delay
     */
    private long calculateBackoff(int retryCount, RetryPolicy policy) {
        long initialInterval = policy.getInitialIntervalMs();
        int multiplier = policy.getBackoffMultiplier();
        long maxInterval = policy.getMaxIntervalMs();

        long backoff = (long) (initialInterval * Math.pow(multiplier, retryCount));
        return Math.min(backoff, maxInterval);
    }

    /**
     * Get timeout for job
     */
    private long getTimeout(QueuedJob job) {
        // Default timeout: 60 minutes
        // Can be overridden in job request
        return 60;
    }
}
