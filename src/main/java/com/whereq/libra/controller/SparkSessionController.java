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
    public ResponseEntity<List<SessionInfo>> getSessions() {
        log.info("Retrieving all Spark sessions");
        return ResponseEntity.ok(sparkSessionService.getAllSessions());
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session info", description = "Get detailed information about a specific session")
    public ResponseEntity<SessionInfo> getSession(@PathVariable String sessionId) {
        log.info("Retrieving session info for session: {}", sessionId);
        return ResponseEntity.ok(sparkSessionService.getSessionInfo(sessionId));
    }

    @PostMapping("/{sessionId}/statements")
    @Operation(summary = "Execute Spark code", description = "Submit Spark code for execution in a session")
    public ResponseEntity<SparkJobResponse> executeStatement(
            @PathVariable String sessionId,
            @Valid @RequestBody SparkJobRequest request) {
        log.info("Executing statement in session: {}", sessionId);
        SparkJobResponse response = sparkSessionService.executeJob(sessionId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete session", description = "Terminate a Spark session")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        log.info("Deleting session: {}", sessionId);
        sparkSessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
