package com.whereq.libra.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for submitting Python-based Spark jobs.
 *
 * @author WhereQ Inc.
 */
@Data
@Schema(description = "Request to submit a Python-based Spark job")
public class PythonSubmitRequest {

    @Schema(description = "Python script file path (mutually exclusive with pythonCode)", example = "/path/to/script.py")
    private String filePath;

    @Schema(description = "Inline Python code (mutually exclusive with filePath)", example = "from pyspark.sql import SparkSession\\nspark = SparkSession.builder.getOrCreate()\\ndf = spark.range(10)\\ndf.show()")
    private String pythonCode;

    @Schema(description = "Script arguments", example = "[\"arg1\", \"arg2\"]")
    private String[] args;

    @Schema(description = "Scheduler pool name for FAIR scheduling", example = "production")
    private String pool;

    @Schema(description = "Spark configuration overrides", example = "{\"spark.executor.memory\": \"2g\", \"spark.executor.cores\": \"2\"}")
    private Map<String, String> sparkConfig;
}
