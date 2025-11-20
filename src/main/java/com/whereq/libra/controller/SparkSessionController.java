package com.whereq.libra.controller;

import com.whereq.libra.dto.SessionInfo;
import com.whereq.libra.dto.SparkJobRequest;
import com.whereq.libra.dto.SparkJobResponse;
import com.whereq.libra.service.SparkSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for managing Spark sessions and executing Spark jobs.
 * Provides endpoints similar to Apache Livy's REST API.
 *
 * @author WhereQ Inc.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Spark Sessions", description = "Manage Spark sessions and execute jobs")
public class SparkSessionController {

    private final SparkSessionService sparkSessionService;

    @GetMapping
    @Operation(summary = "List all sessions", description = "Get information about all active Spark sessions")
    public Flux<SessionInfo> getSessions() {
        log.info("Retrieving all Spark sessions");
        return sparkSessionService.getAllSessionsReactive();
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session info", description = "Get detailed information about a specific session")
    public Mono<ResponseEntity<SessionInfo>> getSession(@PathVariable String sessionId) {
        log.info("Retrieving session info for session: {}", sessionId);
        return sparkSessionService.getSessionInfoReactive(sessionId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{sessionId}/statements")
    @Operation(summary = "Execute Spark code", description = "Submit Spark code for execution in a session")
    public Mono<ResponseEntity<SparkJobResponse>> executeStatement(
            @PathVariable String sessionId,
            @Valid @RequestBody SparkJobRequest request) {
        log.info("Executing statement in session: {}", sessionId);
        return sparkSessionService.executeJobReactive(sessionId, request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete session", description = "Terminate a Spark session")
    public Mono<ResponseEntity<Void>> deleteSession(@PathVariable String sessionId) {
        log.info("Deleting session: {}", sessionId);
        return sparkSessionService.deleteSessionReactive(sessionId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
