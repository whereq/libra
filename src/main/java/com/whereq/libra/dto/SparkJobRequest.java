package com.whereq.libra.dto;

import com.whereq.libra.model.Notifications;
import com.whereq.libra.model.RetryPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Data Transfer Object for Spark job execution request.
 *
 * @author WhereQ Inc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparkJobRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Code to execute (for inline execution).
     * Required for: sql, python
     * Optional for: python-file (when filePath is provided)
     */
    private String code;

    /**
     * Job kind.
     * Options:
     * - sql: Execute SQL query
     * - python: Execute inline Python code
     * - python-file: Execute Python script from file path
     * - jar: Execute JAR with spark-submit (separate process)
     * - jar-class: Execute JAR main class in-JVM (shares SparkSession)
     */
    @Builder.Default
    private String kind = "sql";

    /**
     * File path for python-file or jar kinds.
     * Required when kind = "python-file", "jar", or "jar-class"
     * Can be:
     * - Absolute path: /path/to/file.py or /path/to/app.jar
     * - Relative path: scripts/my_job.py or jars/my-app.jar
     * - Classpath resource: classpath:scripts/my_job.py
     */
    private String filePath;

    /**
     * Main class name for JAR execution.
     * Required when kind = "jar" or "jar-class"
     * Must be fully qualified class name (e.g., com.example.MySparkApp)
     */
    private String mainClass;

    /**
     * Additional arguments to pass to the Python script.
     * Only used when kind = "python-file"
     */
    private String[] args;

    /**
     * Job description.
     */
    private String description;

    /**
     * Spark scheduler pool for FAIR scheduling.
     * Options: default, high-priority, low-priority, interactive
     * If not specified, uses "default" pool.
     */
    private String pool;

    /**
     * Spark configuration overrides for this job.
     * Optional per-job Spark settings that override global configuration.
     * Examples:
     * - "spark.driver.memory": "4g"
     * - "spark.executor.memory": "8g"
     * - "spark.executor.cores": "4"
     * - "spark.executor.instances": "2"
     * - "spark.dynamicAllocation.enabled": "true"
     *
     * NOTE: Resource settings behavior depends on execution mode:
     *
     * 1. jar-class mode (In-JVM):
     *    - Resources CANNOT be changed (uses shared SparkSession)
     *    - These settings are IGNORED for jar-class
     *    - Use global configuration or "jar" mode instead
     *
     * 2. jar mode (spark-submit):
     *    - Full control over driver/executor memory and cores
     *    - Creates separate JVM with specified resources
     *    - Recommended for resource-intensive jobs
     *
     * 3. sql/python/python-file modes (In-JVM):
     *    - Uses shared SparkSession resources
     *    - Cannot override memory/executor settings
     *    - Can set job-level configs like:
     *      - "spark.sql.shuffle.partitions": "200"
     *      - "spark.sql.adaptive.enabled": "true"
     */
    private Map<String, String> sparkConfig;

    /**
     * Execution mode for the job.
     * Options: auto, in-cluster, spark-submit, kubernetes
     * Default: auto (Libra selects based on resource requirements)
     *
     * - auto: Automatically select based on job size
     * - in-cluster: Execute in Libra's JVM (fast, shared resources)
     * - spark-submit: Launch separate process (medium isolation)
     * - kubernetes: Create SparkApplication CRD (high isolation)
     */
    @Builder.Default
    private String executionMode = "auto";

    /**
     * Job priority (1-10, where 10 is highest).
     * Higher priority jobs are scheduled first when resources become available.
     */
    @Builder.Default
    private int priority = 5;

    /**
     * Retry policy for failed jobs.
     * If not specified, uses default policy (3 retries with exponential backoff)
     */
    private RetryPolicy retryPolicy;

    /**
     * Webhook notification configuration.
     * If specified, Libra will send HTTP POST notifications for job events.
     */
    private Notifications notifications;

    /**
     * Maximum execution time in minutes.
     * Job will be terminated if it exceeds this time.
     * Default: 60 minutes
     */
    @Builder.Default
    private int timeoutMinutes = 60;
}
