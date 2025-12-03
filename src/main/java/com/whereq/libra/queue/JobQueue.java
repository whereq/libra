package com.whereq.libra.queue;

import com.whereq.libra.model.QueuedJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Job queue interface for async job submission
 */
public interface JobQueue {
    /**
     * Enqueue a job
     *
     * @param job the job to enqueue
     * @return Mono that completes when job is enqueued
     */
    Mono<Void> enqueue(QueuedJob job);

    /**
     * Consume jobs from the queue as a reactive stream
     *
     * @return Flux of queued jobs
     */
    Flux<QueuedJob> consumeAsFlux();

    /**
     * Acknowledge successful processing of a job
     *
     * @param jobId the job identifier
     * @return Mono that completes when acknowledged
     */
    Mono<Void> acknowledge(String jobId);

    /**
     * Remove a job from the queue (for cancellation)
     *
     * @param jobId the job identifier
     * @return Mono that completes when removed
     */
    Mono<Void> remove(String jobId);

    /**
     * Get current queue size
     *
     * @return Mono with queue size
     */
    Mono<Long> size();

    /**
     * Check if queue is full
     *
     * @param maxSize maximum allowed size
     * @return Mono with true if queue is full
     */
    default Mono<Boolean> isFull(long maxSize) {
        return size().map(s -> s >= maxSize);
    }
}
