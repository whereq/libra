package com.whereq.libra.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Resource requirements for a Spark job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRequirement implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Number of executor instances
     */
    private int executorCount;

    /**
     * Total CPU cores needed (executors + driver)
     */
    private int totalCores;

    /**
     * Total memory in MB (executors + driver)
     */
    private long totalMemoryMB;

    /**
     * Driver cores
     */
    private int driverCores;

    /**
     * Driver memory in MB
     */
    private long driverMemoryMB;

    /**
     * Cores per executor
     */
    private int executorCores;

    /**
     * Memory per executor in MB
     */
    private long executorMemoryMB;

    /**
     * Get total memory in GB
     */
    public double getTotalMemoryGB() {
        return totalMemoryMB / 1024.0;
    }

    /**
     * Check if this is a large job (suitable for Kubernetes mode)
     */
    public boolean isLargeJob() {
        return executorCount > 20 || getTotalMemoryGB() > 100;
    }

    /**
     * Check if this job needs isolation (suitable for spark-submit mode)
     */
    public boolean needsIsolation() {
        return executorCount > 5 || getTotalMemoryGB() > 32;
    }
}
