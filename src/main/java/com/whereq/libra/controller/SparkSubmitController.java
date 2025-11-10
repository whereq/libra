package com.whereq.libra.controller;

import com.whereq.libra.dto.JarSubmitRequest;
import com.whereq.libra.dto.JobSubmitResponse;
import com.whereq.libra.dto.PythonSubmitRequest;
import com.whereq.libra.service.JarJobExecutor;
import com.whereq.libra.service.PythonJobExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for submitting Spark jobs using spark-submit style operations.
 * Supports JAR and Python job submissions with full spark-submit configuration options.
 *
 * @author WhereQ Inc.
 */
@RestController
@RequestMapping("/api/v1/spark/jobs")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Spark Submit", description = "Submit Spark jobs (JAR/Python) with spark-submit configurations")
public class SparkSubmitController {

    private final JarJobExecutor jarJobExecutor;
    private final PythonJobExecutor pythonJobExecutor;

    @PostMapping("/submit/jar")
    @Operation(
        summary = "Submit JAR job",
        description = "Submit a Spark job from a JAR file with optional spark-submit configurations",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = JarSubmitRequest.class),
                examples = {
                    @ExampleObject(
                        name = "In-JVM Execution",
                        value = """
                        {
                          "jarPath": "/app/jars/my-spark-app.jar",
                          "mainClass": "com.example.MySparkApp",
                          "args": ["input.csv", "output.parquet"],
                          "executionMode": "IN_JVM",
                          "pool": "production",
                          "sparkConfig": {
                            "spark.executor.memory": "2g",
                            "spark.executor.cores": "2"
                          }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Spark Submit Execution",
                        value = """
                        {
                          "jarPath": "/app/jars/my-spark-app.jar",
                          "mainClass": "com.example.MySparkApp",
                          "args": ["input.csv", "output.parquet"],
                          "executionMode": "SPARK_SUBMIT",
                          "sparkConfig": {
                            "spark.driver.memory": "1g",
                            "spark.executor.memory": "2g",
                            "spark.executor.cores": "2",
                            "spark.executor.instances": "3",
                            "spark.dynamicAllocation.enabled": "false"
                          }
                        }
                        """
                    )
                }
            )
        )
    )
    public ResponseEntity<JobSubmitResponse> submitJarJob(@Valid @RequestBody JarSubmitRequest request) {
        log.info("Received JAR job submission request: jar={}, mainClass={}, mode={}",
                request.getJarPath(), request.getMainClass(), request.getExecutionMode());

        long startTime = System.currentTimeMillis();
        JobSubmitResponse response;

        try {
            String output;
            if (request.getExecutionMode() == JarSubmitRequest.ExecutionMode.IN_JVM) {
                output = jarJobExecutor.executeJarInJVM(
                        request.getJarPath(),
                        request.getMainClass(),
                        request.getArgs()
                );
            } else {
                output = jarJobExecutor.executeJarWithSparkSubmit(
                        request.getJarPath(),
                        request.getMainClass(),
                        request.getArgs(),
                        request.getPool(),
                        request.getSparkConfig()
                );
            }

            long executionTime = System.currentTimeMillis() - startTime;
            response = JobSubmitResponse.builder()
                    .status(JobSubmitResponse.JobStatus.SUCCESS)
                    .output(output)
                    .executionTimeMs(executionTime)
                    .build();

            log.info("JAR job completed successfully in {}ms", executionTime);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("JAR job failed after {}ms", executionTime, e);

            response = JobSubmitResponse.builder()
                    .status(JobSubmitResponse.JobStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();

            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/submit/python")
    @Operation(
        summary = "Submit Python job",
        description = "Submit a Spark job from a Python file or inline code with optional spark-submit configurations",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PythonSubmitRequest.class),
                examples = {
                    @ExampleObject(
                        name = "Python File",
                        value = """
                        {
                          "filePath": "/app/scripts/wordcount.py",
                          "args": ["input.txt", "output"],
                          "pool": "development",
                          "sparkConfig": {
                            "spark.executor.memory": "1g",
                            "spark.executor.cores": "1"
                          }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Inline Python Code",
                        value = """
                        {
                          "pythonCode": "from pyspark.sql import SparkSession\\n\\nspark = SparkSession.builder.appName(\\"Test\\").getOrCreate()\\ndf = spark.range(100)\\nprint(f\\"Count: {df.count()}\\")\\nspark.stop()",
                          "sparkConfig": {
                            "spark.executor.memory": "512m"
                          }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "Advanced Configuration",
                        value = """
                        {
                          "filePath": "/app/scripts/ml_pipeline.py",
                          "args": ["--input", "/data/train.parquet", "--output", "/models/output"],
                          "pool": "ml-training",
                          "sparkConfig": {
                            "spark.driver.memory": "4g",
                            "spark.driver.cores": "2",
                            "spark.executor.memory": "8g",
                            "spark.executor.cores": "4",
                            "spark.executor.instances": "10",
                            "spark.dynamicAllocation.enabled": "false",
                            "spark.sql.shuffle.partitions": "200",
                            "spark.default.parallelism": "200"
                          }
                        }
                        """
                    )
                }
            )
        )
    )
    public ResponseEntity<JobSubmitResponse> submitPythonJob(@Valid @RequestBody PythonSubmitRequest request) {
        // Validate that either filePath or pythonCode is provided, but not both
        if ((request.getFilePath() == null || request.getFilePath().isBlank()) &&
            (request.getPythonCode() == null || request.getPythonCode().isBlank())) {
            JobSubmitResponse response = JobSubmitResponse.builder()
                    .status(JobSubmitResponse.JobStatus.FAILED)
                    .errorMessage("Either filePath or pythonCode must be provided")
                    .build();
            return ResponseEntity.badRequest().body(response);
        }

        if (request.getFilePath() != null && !request.getFilePath().isBlank() &&
            request.getPythonCode() != null && !request.getPythonCode().isBlank()) {
            JobSubmitResponse response = JobSubmitResponse.builder()
                    .status(JobSubmitResponse.JobStatus.FAILED)
                    .errorMessage("Only one of filePath or pythonCode should be provided, not both")
                    .build();
            return ResponseEntity.badRequest().body(response);
        }

        log.info("Received Python job submission request: filePath={}, hasCode={}",
                request.getFilePath(), request.getPythonCode() != null && !request.getPythonCode().isBlank());

        long startTime = System.currentTimeMillis();
        JobSubmitResponse response;

        try {
            String output;
            if (request.getPythonCode() != null && !request.getPythonCode().isBlank()) {
                output = pythonJobExecutor.executePythonCode(
                        request.getPythonCode(),
                        request.getPool(),
                        request.getSparkConfig()
                );
            } else {
                output = pythonJobExecutor.executePythonFile(
                        request.getFilePath(),
                        request.getArgs(),
                        request.getPool(),
                        request.getSparkConfig()
                );
            }

            long executionTime = System.currentTimeMillis() - startTime;
            response = JobSubmitResponse.builder()
                    .status(JobSubmitResponse.JobStatus.SUCCESS)
                    .output(output)
                    .executionTimeMs(executionTime)
                    .build();

            log.info("Python job completed successfully in {}ms", executionTime);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Python job failed after {}ms", executionTime, e);

            response = JobSubmitResponse.builder()
                    .status(JobSubmitResponse.JobStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .output(e.toString())
                    .executionTimeMs(executionTime)
                    .build();

            return ResponseEntity.status(500).body(response);
        }
    }
}
