package com.whereq.libra.service;

import com.whereq.libra.model.QueuedJob;
import com.whereq.libra.queue.JobQueue;
import com.whereq.libra.resource.ResourceMonitor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

/**
 * Admission control for job submissions
 * Decides whether to admit, queue, or reject jobs based on resource availability
 */
@Slf4j
@Service
public class AdmissionController {

    @Value("${libra.queue.max-size:1000}")
    private long maxQueueSize;

    @Autowired
    private ResourceMonitor resourceMonitor;

    @Autowired
    private JobQueue jobQueue;

    @Autowired
    private MeterRegistry meterRegistry;

    private Counter admittedCounter;
    private Counter queuedCounter;
    private Counter rejectedCounter;

    @PostConstruct
    public void initialize() {
        admittedCounter = Counter.builder("libra.admission.admitted")
            .description("Number of jobs admitted immediately")
            .register(meterRegistry);

        queuedCounter = Counter.builder("libra.admission.queued")
            .description("Number of jobs queued for later execution")
            .register(meterRegistry);

        rejectedCounter = Counter.builder("libra.admission.rejected")
            .description("Number of jobs rejected due to full queue")
            .register(meterRegistry);
    }

    /**
     * Admit or queue a job based on resource availability
     *
     * @param job the queued job
     * @return Mono with admission decision
     */
    public Mono<AdmissionDecision> admitJob(QueuedJob job) {
        return jobQueue.isFull(maxQueueSize)
            .flatMap(isFull -> {
                if (isFull) {
                    // Queue is full, reject
                    rejectedCounter.increment();
                    log.warn("Job {} rejected: queue is full (size >= {})", job.getJobId(), maxQueueSize);
                    return Mono.just(AdmissionDecision.REJECT);
                }

                // Check if resources are available
                boolean canRun = resourceMonitor.canAccommodate(job.getResourceRequirement());

                if (canRun) {
                    // Resources available, admit immediately
                    admittedCounter.increment();
                    log.info("Job {} admitted: resources available ({})",
                        job.getJobId(), resourceMonitor.getResourceSummary());
                    return Mono.just(AdmissionDecision.ADMIT);
                } else {
                    // Resources unavailable, queue for later
                    queuedCounter.increment();
                    log.info("Job {} queued: resources unavailable ({})",
                        job.getJobId(), resourceMonitor.getResourceSummary());
                    return Mono.just(AdmissionDecision.QUEUE);
                }
            });
    }

    /**
     * Admission decision
     */
    public enum AdmissionDecision {
        /**
         * Admit job immediately (resources available)
         */
        ADMIT,

        /**
         * Queue job for later execution (resources unavailable)
         */
        QUEUE,

        /**
         * Reject job (queue full)
         */
        REJECT
    }
}
