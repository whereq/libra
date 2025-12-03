package com.whereq.libra.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents current resource usage
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUsage {
    public static final ResourceUsage ZERO = new ResourceUsage(0, 0);

    /**
     * CPU cores in use
     */
    private int cpuCores;

    /**
     * Memory in GB in use
     */
    private double memoryGB;

    /**
     * Add two resource usages
     */
    public ResourceUsage add(ResourceUsage other) {
        return new ResourceUsage(
            this.cpuCores + other.cpuCores,
            this.memoryGB + other.memoryGB
        );
    }

    /**
     * Create from ResourceRequirement
     */
    public ResourceUsage(ResourceRequirement requirement) {
        this.cpuCores = requirement.getTotalCores();
        this.memoryGB = requirement.getTotalMemoryGB();
    }
}
