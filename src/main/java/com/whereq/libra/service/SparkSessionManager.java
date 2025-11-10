package com.whereq.libra.service;

import com.whereq.libra.config.LibraProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages SparkSession lifecycle based on configured mode.
 *
 * Supports two modes:
 * 1. SHARED: Single SparkSession for all requests (default)
 * 2. ISOLATED: One SparkSession per session ID
 *
 * @author WhereQ Inc.
 */
@Component
@Slf4j
public class SparkSessionManager {

    private final LibraProperties libraProperties;
    private final SparkConf sparkConf;

    // Shared session (for SHARED mode)
    private SparkSession sharedSession;

    // Session pool (for ISOLATED mode)
    private final Map<String, SessionWrapper> sessions = new ConcurrentHashMap<>();

    // Background cleanup thread
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    // Temporary file for fairscheduler.xml
    private File fairSchedulerFile;

    @Autowired
    public SparkSessionManager(LibraProperties libraProperties, SparkConf sparkConf) {
        this.libraProperties = libraProperties;
        this.sparkConf = sparkConf;

        log.info("Initializing SparkSessionManager in {} mode",
                libraProperties.getSession().getMode());

        // Extract fairscheduler.xml from classpath to temp file if FAIR scheduler is enabled
        try {
            if ("FAIR".equalsIgnoreCase(sparkConf.get("spark.scheduler.mode", "FIFO"))) {
                extractFairSchedulerConfig();
            }
        } catch (Exception e) {
            log.warn("Failed to extract fairscheduler.xml, FAIR scheduling may not work properly", e);
        }

        if (libraProperties.getSession().getMode() == LibraProperties.SessionMode.SHARED) {
            // Create shared session at startup
            this.sharedSession = createSparkSession("shared");
            log.info("Created shared SparkSession: {}", sharedSession.sparkContext().applicationId());
        } else {
            // Start cleanup thread for session timeout
            startCleanupThread();
        }
    }

    /**
     * Get or create SparkSession for the given session ID.
     *
     * @param sessionId Session identifier
     * @return SparkSession instance
     */
    public SparkSession getOrCreateSession(String sessionId) {
        if (libraProperties.getSession().getMode() == LibraProperties.SessionMode.SHARED) {
            return sharedSession;
        }

        return sessions.computeIfAbsent(sessionId, this::createSessionWrapper).getSession();
    }

    /**
     * Get SparkSession for session ID (returns null if not exists).
     */
    public SparkSession getSession(String sessionId) {
        if (libraProperties.getSession().getMode() == LibraProperties.SessionMode.SHARED) {
            return sharedSession;
        }

        SessionWrapper wrapper = sessions.get(sessionId);
        if (wrapper != null) {
            wrapper.updateLastAccess();
            return wrapper.getSession();
        }
        return null;
    }

    /**
     * Delete session by ID.
     */
    public void deleteSession(String sessionId) {
        if (libraProperties.getSession().getMode() == LibraProperties.SessionMode.SHARED) {
            log.warn("Cannot delete session in SHARED mode");
            return;
        }

        SessionWrapper wrapper = sessions.remove(sessionId);
        if (wrapper != null) {
            log.info("Closing session: {}", sessionId);
            try {
                wrapper.getSession().close();
            } catch (Exception e) {
                log.error("Error closing session: {}", sessionId, e);
            }
        }
    }

    /**
     * Get number of active sessions.
     */
    public int getActiveSessionCount() {
        if (libraProperties.getSession().getMode() == LibraProperties.SessionMode.SHARED) {
            return sharedSession != null ? 1 : 0;
        }
        return sessions.size();
    }

    /**
     * Check if can create new session.
     */
    public boolean canCreateNewSession() {
        if (libraProperties.getSession().getMode() == LibraProperties.SessionMode.SHARED) {
            return true; // Always true for shared mode
        }
        return sessions.size() < libraProperties.getSession().getMaxSessions();
    }

    /**
     * Set Spark local property for current thread (for pool assignment).
     * This allows jobs to use different resource pools.
     */
    public void setLocalProperty(String sessionId, String key, String value) {
        SparkSession session = getSession(sessionId);
        if (session != null) {
            session.sparkContext().setLocalProperty(key, value);
        }
    }

    private SessionWrapper createSessionWrapper(String sessionId) {
        if (!canCreateNewSession()) {
            throw new IllegalStateException(
                String.format("Maximum sessions limit reached: %d",
                    libraProperties.getSession().getMaxSessions())
            );
        }

        log.info("Creating new SparkSession for session: {}", sessionId);
        SparkSession session = createSparkSession(sessionId);
        return new SessionWrapper(session);
    }

    private SparkSession createSparkSession(String sessionId) {
        return SparkSession.builder()
                .appName(sparkConf.get("spark.app.name", "whereq-libra") + "-" + sessionId)
                .config(sparkConf)
                .getOrCreate();
    }

    private void startCleanupThread() {
        int timeoutMinutes = libraProperties.getSession().getTimeoutMinutes();

        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);

                sessions.entrySet().removeIf(entry -> {
                    if (entry.getValue().getLastAccess().isBefore(cutoff)) {
                        log.info("Cleaning up inactive session: {}", entry.getKey());
                        try {
                            entry.getValue().getSession().close();
                        } catch (Exception e) {
                            log.error("Error closing session during cleanup: {}", entry.getKey(), e);
                        }
                        return true;
                    }
                    return false;
                });
            } catch (Exception e) {
                log.error("Error during session cleanup", e);
            }
        }, 5, 5, TimeUnit.MINUTES);

        log.info("Started session cleanup thread (timeout: {} minutes)", timeoutMinutes);
    }

    /**
     * Extract fairscheduler.xml from classpath to a temporary file.
     */
    private void extractFairSchedulerConfig() throws Exception {
        ClassPathResource resource = new ClassPathResource("fairscheduler.xml");
        if (!resource.exists()) {
            log.warn("fairscheduler.xml not found in classpath, skipping FAIR scheduler configuration");
            return;
        }

        // Create temp file
        fairSchedulerFile = File.createTempFile("fairscheduler", ".xml");
        fairSchedulerFile.deleteOnExit();

        // Copy content from classpath to temp file
        try (InputStream inputStream = resource.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(fairSchedulerFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        // Set the configuration for Spark
        sparkConf.set("spark.scheduler.allocation.file", fairSchedulerFile.getAbsolutePath());
        log.info("Extracted fairscheduler.xml to: {}", fairSchedulerFile.getAbsolutePath());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SparkSessionManager");

        cleanupExecutor.shutdown();

        if (sharedSession != null) {
            log.info("Closing shared SparkSession");
            try {
                sharedSession.close();
            } catch (Exception e) {
                log.error("Error closing shared session", e);
            }
        }

        sessions.forEach((id, wrapper) -> {
            log.info("Closing session: {}", id);
            try {
                wrapper.getSession().close();
            } catch (Exception e) {
                log.error("Error closing session: {}", id, e);
            }
        });
        sessions.clear();

        // Clean up temp fairscheduler file
        if (fairSchedulerFile != null && fairSchedulerFile.exists()) {
            try {
                Files.delete(fairSchedulerFile.toPath());
                log.info("Deleted temporary fairscheduler.xml file");
            } catch (Exception e) {
                log.warn("Failed to delete temporary fairscheduler.xml file", e);
            }
        }
    }

    /**
     * Wrapper class to track session metadata.
     */
    private static class SessionWrapper {
        private final SparkSession session;
        private volatile LocalDateTime lastAccess;

        SessionWrapper(SparkSession session) {
            this.session = session;
            this.lastAccess = LocalDateTime.now();
        }

        SparkSession getSession() {
            return session;
        }

        LocalDateTime getLastAccess() {
            return lastAccess;
        }

        void updateLastAccess() {
            this.lastAccess = LocalDateTime.now();
        }
    }
}
