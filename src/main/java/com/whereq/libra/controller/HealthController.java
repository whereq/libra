package com.whereq.libra.controller;

import com.whereq.libra.service.SparkSessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller to verify service and Spark status.
 *
 * @author WhereQ Inc.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service health check endpoints")
public class HealthController {

    @Autowired
    private SparkSessionManager sessionManager;

    @GetMapping
    @Operation(summary = "Health check", description = "Check if the service and Spark are running")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return sessionManager.getOrCreateSessionReactive("default")
                .map(sparkSession -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("service", "whereq-libra");

                    Map<String, String> sparkInfo = new HashMap<>();
                    sparkInfo.put("version", sparkSession.version());
                    sparkInfo.put("applicationId", sparkSession.sparkContext().applicationId());
                    sparkInfo.put("master", sparkSession.sparkContext().master());
                    sparkInfo.put("status", "CONNECTED");
                    sparkInfo.put("activeSessions", String.valueOf(sessionManager.getActiveSessionCount()));

                    health.put("spark", sparkInfo);
                    return ResponseEntity.ok(health);
                })
                .onErrorResume(e -> {
                    Map<String, Object> health = new HashMap<>();
                    health.put("status", "UP");
                    health.put("service", "whereq-libra");

                    Map<String, String> sparkInfo = new HashMap<>();
                    sparkInfo.put("status", "ERROR");
                    sparkInfo.put("error", e.getMessage());
                    health.put("spark", sparkInfo);

                    return Mono.just(ResponseEntity.ok(health));
                });
    }
}
