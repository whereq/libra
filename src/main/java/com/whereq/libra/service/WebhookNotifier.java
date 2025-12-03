package com.whereq.libra.service;

import com.whereq.libra.model.JobResult;
import com.whereq.libra.model.JobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Service for sending webhook notifications
 */
@Slf4j
@Service
public class WebhookNotifier {

    @Autowired
    private WebClient.Builder webClientBuilder;

    private static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Notify webhook about job status change
     *
     * @param webhookUrl webhook URL
     * @param jobId job identifier
     * @param status job status
     * @param data additional data (result or error)
     * @return Mono that completes when notification sent
     */
    public Mono<Void> notify(String webhookUrl, String jobId, JobStatus status, Object data) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return Mono.empty();
        }

        Map<String, Object> payload = buildPayload(jobId, status, data);

        return webClientBuilder.build()
            .post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .timeout(WEBHOOK_TIMEOUT)
            .doOnSuccess(response -> log.info("Webhook notification sent for job {}: {} - {}",
                jobId, status, response.getStatusCode()))
            .doOnError(error -> log.error("Failed to send webhook notification for job {}: {}",
                jobId, error.getMessage()))
            .onErrorResume(e -> Mono.empty()) // Don't fail job if webhook fails
            .then();
    }

    private Map<String, Object> buildPayload(String jobId, JobStatus status, Object data) {
        Map<String, Object> payload = Map.of(
            "jobId", jobId,
            "status", status.name(),
            "timestamp", System.currentTimeMillis()
        );

        if (data instanceof JobResult result) {
            return Map.of(
                "jobId", jobId,
                "status", status.name(),
                "result", result,
                "timestamp", System.currentTimeMillis()
            );
        } else if (data instanceof Throwable error) {
            return Map.of(
                "jobId", jobId,
                "status", status.name(),
                "error", error.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
        }

        return payload;
    }
}
