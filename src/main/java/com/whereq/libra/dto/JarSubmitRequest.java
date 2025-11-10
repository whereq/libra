package com.whereq.libra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for submitting JAR-based Spark jobs.
 *
 * @author WhereQ Inc.
 */
@Data
@Schema(description = "Request to submit a JAR-based Spark job")
public class JarSubmitRequest {

    @NotBlank(message = "JAR path is required")
    @Schema(description = "Path to the JAR file (absolute, relative, or classpath:)", example = "/path/to/app.jar")
    private String jarPath;

    @NotBlank(message = "Main class is required")
    @Schema(description = "Fully qualified main class name", example = "com.example.SparkApp")
    private String mainClass;

    @Schema(description = "Application arguments", example = "[\"arg1\", \"arg2\"]")
    private String[] args;

    @Schema(description = "Execution mode: IN_JVM or SPARK_SUBMIT", example = "SPARK_SUBMIT", defaultValue = "SPARK_SUBMIT")
    private ExecutionMode executionMode = ExecutionMode.SPARK_SUBMIT;

    @Schema(description = "Scheduler pool name for FAIR scheduling", example = "production")
    private String pool;

    @Schema(description = "Spark configuration overrides", example = "{\"spark.executor.memory\": \"2g\", \"spark.executor.cores\": \"2\"}")
    private Map<String, String> sparkConfig;

    public enum ExecutionMode {
        /**
         * Execute JAR in the same JVM (shares SparkSession).
         */
        IN_JVM,

        /**
         * Execute JAR using spark-submit (separate process).
         */
        SPARK_SUBMIT
    }
}
