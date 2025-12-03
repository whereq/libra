package com.whereq.libra.executor;

import com.whereq.libra.dto.SparkJobResponse;
import com.whereq.libra.model.ExecutionMode;
import com.whereq.libra.model.JobResult;
import com.whereq.libra.model.JobStatus;
import com.whereq.libra.model.QueuedJob;
import com.whereq.libra.service.SparkSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Executor for in-cluster (in-JVM) job execution
 * Uses existing SparkSessionService for execution
 */
@Slf4j
@Service
public class InClusterExecutor implements JobExecutor {

    @Autowired
    private SparkSessionService sparkSessionService;

    @Override
    public JobResult executeJob(QueuedJob job) throws Exception {
        log.info("Executing job {} in-cluster", job.getJobId());

        long startTime = System.currentTimeMillis();

        try {
            // Use existing SparkSessionService to execute the job
            SparkJobResponse response = sparkSessionService.executeJob("default", job.getRequest());

            long executionTime = (System.currentTimeMillis() - startTime) / 1000;

            // Check if job succeeded (state can be "available", "error", etc.)
            boolean isSuccess = response.getError() == null || response.getError().isEmpty();

            if (isSuccess) {
                // Convert result to string output
                String output = response.getResult() != null ? response.getResult().toString() : "";

                return JobResult.builder()
                    .jobId(job.getJobId())
                    .status(JobStatus.SUCCEEDED)
                    .output(output)
                    .executionTimeSeconds(executionTime)
                    .executionMode(ExecutionMode.IN_CLUSTER)
                    .build();
            } else {
                return JobResult.builder()
                    .jobId(job.getJobId())
                    .status(JobStatus.FAILED)
                    .errorMessage(response.getError())
                    .output(response.getResult() != null ? response.getResult().toString() : "")
                    .executionTimeSeconds(executionTime)
                    .executionMode(ExecutionMode.IN_CLUSTER)
                    .build();
            }

        } catch (Exception e) {
            long executionTime = (System.currentTimeMillis() - startTime) / 1000;

            log.error("Job {} failed in-cluster execution", job.getJobId(), e);

            return JobResult.builder()
                .jobId(job.getJobId())
                .status(JobStatus.FAILED)
                .errorMessage(e.getMessage())
                .executionTimeSeconds(executionTime)
                .executionMode(ExecutionMode.IN_CLUSTER)
                .build();
        }
    }
}
