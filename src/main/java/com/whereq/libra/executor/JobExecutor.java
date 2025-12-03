package com.whereq.libra.executor;

import com.whereq.libra.model.JobResult;
import com.whereq.libra.model.QueuedJob;

/**
 * Interface for job execution strategies
 */
public interface JobExecutor {
    /**
     * Execute a job synchronously (blocking)
     *
     * @param job the queued job
     * @return job result
     * @throws Exception if execution fails
     */
    JobResult executeJob(QueuedJob job) throws Exception;

    /**
     * Cancel a running job
     *
     * @param jobId job identifier
     */
    default void cancel(String jobId) {
        // Default: no-op
        // Override in implementations that support cancellation
    }
}
