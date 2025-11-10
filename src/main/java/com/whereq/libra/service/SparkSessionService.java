package com.whereq.libra.service;

import com.whereq.libra.dto.SessionInfo;
import com.whereq.libra.dto.SparkJobRequest;
import com.whereq.libra.dto.SparkJobResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing Spark sessions and executing Spark jobs.
 *
 * Supports parallel job execution through:
 * 1. FAIR scheduler with resource pools
 * 2. Thread-safe SparkSession operations
 * 3. Configurable session management (SHARED or ISOLATED)
 *
 * @author WhereQ Inc.
 */
@Service
@Slf4j
public class SparkSessionService {

    @Autowired
    private SparkSessionManager sessionManager;

    @Autowired
    private PythonJobExecutor pythonJobExecutor;

    @Autowired
    private JarJobExecutor jarJobExecutor;

    public List<SessionInfo> getAllSessions() {
        // Return active session info
        SessionInfo info = getSessionInfo("default");
        return List.of(info);
    }

    public SessionInfo getSessionInfo(String sessionId) {
        SparkSession session = sessionManager.getOrCreateSession(sessionId);

        return SessionInfo.builder()
                .sessionId(sessionId)
                .appId(session.sparkContext().applicationId())
                .state("RUNNING")
                .master(session.sparkContext().master())
                .sparkVersion(session.version())
                .createdAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .build();
    }

    public SparkJobResponse executeJob(String sessionId, SparkJobRequest request) {
        String jobId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("Executing job {} in session {} (parallel execution enabled)", jobId, sessionId);

        try {
            // Get or create session for this request
            SparkSession session = sessionManager.getOrCreateSession(sessionId);

            // Set pool for FAIR scheduling (optional - can be based on request)
            String pool = request.getPool() != null ? request.getPool() : "default";
            sessionManager.setLocalProperty(sessionId, "spark.scheduler.pool", pool);

            Object result = null;

            if ("sql".equalsIgnoreCase(request.getKind())) {
                // Execute SQL query
                Dataset<Row> df = session.sql(request.getCode());

                // Collect results and format as string
                List<Row> rows = df.collectAsList();
                StringBuilder resultBuilder = new StringBuilder();

                // Add schema
                resultBuilder.append("Schema: ").append(df.schema().simpleString()).append("\n\n");

                // Add data rows (limit to 20 for display)
                int limit = Math.min(rows.size(), 20);
                for (int i = 0; i < limit; i++) {
                    resultBuilder.append(rows.get(i).toString()).append("\n");
                }

                if (rows.size() > 20) {
                    resultBuilder.append(String.format("\n... showing 20 of %d rows", rows.size()));
                } else {
                    resultBuilder.append(String.format("\nTotal rows: %d", rows.size()));
                }

                result = resultBuilder.toString();

            } else if ("python".equalsIgnoreCase(request.getKind())) {
                // Execute inline Python code
                if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                    throw new IllegalArgumentException("Python code is required for kind 'python'");
                }
                result = pythonJobExecutor.executePythonCode(
                    request.getCode(),
                    pool,
                    request.getSparkConfig()
                );

            } else if ("python-file".equalsIgnoreCase(request.getKind())) {
                // Execute Python script from file
                if (request.getFilePath() == null || request.getFilePath().trim().isEmpty()) {
                    throw new IllegalArgumentException("File path is required for kind 'python-file'");
                }
                result = pythonJobExecutor.executePythonFile(
                    request.getFilePath(),
                    request.getArgs(),
                    pool,
                    request.getSparkConfig()
                );

            } else if ("jar-class".equalsIgnoreCase(request.getKind())) {
                // Execute JAR main class in-JVM (shares SparkSession)
                if (request.getFilePath() == null || request.getFilePath().trim().isEmpty()) {
                    throw new IllegalArgumentException("File path (JAR) is required for kind 'jar-class'");
                }
                if (request.getMainClass() == null || request.getMainClass().trim().isEmpty()) {
                    throw new IllegalArgumentException("Main class is required for kind 'jar-class'");
                }

                // Check if resource configurations are provided
                boolean hasResourceConfig = request.getSparkConfig() != null &&
                    (request.getSparkConfig().containsKey("spark.driver.memory") ||
                     request.getSparkConfig().containsKey("spark.executor.memory") ||
                     request.getSparkConfig().containsKey("spark.executor.cores") ||
                     request.getSparkConfig().containsKey("spark.executor.instances") ||
                     request.getSparkConfig().containsKey("spark.driver.cores"));

                if (hasResourceConfig) {
                    // Auto-switch to jar mode (spark-submit) for custom resources
                    log.warn("jar-class mode requested with custom resource configuration. " +
                            "Automatically switching to 'jar' mode (spark-submit) to honor resource settings. " +
                            "To use shared resources, remove sparkConfig or use global configuration.");
                    result = jarJobExecutor.executeJarWithSparkSubmit(
                        request.getFilePath(),
                        request.getMainClass(),
                        request.getArgs(),
                        pool,
                        request.getSparkConfig()
                    );
                } else {
                    // Execute in-JVM with shared session
                    log.info("Executing JAR in-JVM - SparkSession.getOrCreate() will find existing session");
                    result = jarJobExecutor.executeJarInJVM(
                        request.getFilePath(),
                        request.getMainClass(),
                        request.getArgs()
                    );
                }

            } else if ("jar".equalsIgnoreCase(request.getKind())) {
                // Execute JAR with spark-submit (separate process)
                if (request.getFilePath() == null || request.getFilePath().trim().isEmpty()) {
                    throw new IllegalArgumentException("File path (JAR) is required for kind 'jar'");
                }
                if (request.getMainClass() == null || request.getMainClass().trim().isEmpty()) {
                    throw new IllegalArgumentException("Main class is required for kind 'jar'");
                }
                result = jarJobExecutor.executeJarWithSparkSubmit(
                    request.getFilePath(),
                    request.getMainClass(),
                    request.getArgs(),
                    pool,
                    request.getSparkConfig()
                );

            } else {
                // For other types, you'd need to implement appropriate handlers
                log.warn("Job kind '{}' not fully implemented yet", request.getKind());
                result = "Job type '" + request.getKind() + "' execution not yet implemented";
            }

            LocalDateTime endTime = LocalDateTime.now();
            long executionTime = java.time.Duration.between(startTime, endTime).toMillis();

            return SparkJobResponse.builder()
                    .jobId(jobId)
                    .sessionId(sessionId)
                    .state("SUCCESS")
                    .result(result)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("Error executing job {}", jobId, e);
            LocalDateTime endTime = LocalDateTime.now();
            long executionTime = java.time.Duration.between(startTime, endTime).toMillis();

            return SparkJobResponse.builder()
                    .jobId(jobId)
                    .sessionId(sessionId)
                    .state("FAILED")
                    .error(e.getMessage())
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    public void deleteSession(String sessionId) {
        log.info("Session deletion requested for: {}", sessionId);
        sessionManager.deleteSession(sessionId);
    }
}
