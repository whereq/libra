package com.whereq.libra.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class SparkJobRequest {

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
}
