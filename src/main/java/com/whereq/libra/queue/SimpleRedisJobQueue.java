package com.whereq.libra.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whereq.libra.model.QueuedJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified Redis-based job queue using Lists
 * Phase 1 implementation - will be enhanced to use Streams in Phase 2
 */
@Slf4j
@Service
public class SimpleRedisJobQueue implements JobQueue {

    private static final String QUEUE_KEY = "libra:jobs:queue";
    private static final String PROCESSING_KEY_PREFIX = "libra:jobs:processing:";

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // In-memory tracking for Phase 1
    private final ConcurrentHashMap<String, QueuedJob> processingJobs = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> enqueue(QueuedJob job) {
        return Mono.fromCallable(() -> {
            try {
                String json = objectMapper.writeValueAsString(job);
                return json;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize job", e);
            }
        })
        .flatMap(json -> redisTemplate.opsForList().rightPush(QUEUE_KEY, json))
        .doOnSuccess(size -> log.info("Enqueued job {}, queue size: {}", job.getJobId(), size))
        .then()
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<QueuedJob> consumeAsFlux() {
        return Flux.interval(Duration.ofSeconds(1))
            .flatMap(tick -> redisTemplate.opsForList().leftPop(QUEUE_KEY)
                .flatMap(json -> {
                    try {
                        QueuedJob job = objectMapper.readValue(json, QueuedJob.class);
                        processingJobs.put(job.getJobId(), job);
                        log.debug("Consumed job {} from queue", job.getJobId());
                        return Mono.just(job);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize job", e);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.empty())
            )
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> acknowledge(String jobId) {
        return Mono.fromRunnable(() -> {
            QueuedJob removed = processingJobs.remove(jobId);
            if (removed != null) {
                log.info("Acknowledged job {}", jobId);
            } else {
                log.warn("Attempted to acknowledge unknown job {}", jobId);
            }
        }).then();
    }

    @Override
    public Mono<Void> remove(String jobId) {
        return Mono.fromRunnable(() -> {
            QueuedJob removed = processingJobs.remove(jobId);
            if (removed != null) {
                log.info("Removed job {} from processing", jobId);
            }
        }).then();
    }

    @Override
    public Mono<Long> size() {
        return redisTemplate.opsForList().size(QUEUE_KEY)
            .defaultIfEmpty(0L);
    }
}
