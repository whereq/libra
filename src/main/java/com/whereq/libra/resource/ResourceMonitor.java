package com.whereq.libra.resource;

import com.whereq.libra.model.ResourceRequirement;
import com.whereq.libra.model.ResourceUsage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitor resource usage and availability
 */
@Slf4j
@Component
public class ResourceMonitor {

    @Value("${libra.resources.limits.cpu-cores:16}")
    private int cpuLimitCores;

    @Value("${libra.resources.limits.memory-gb:64}")
    private double memoryLimitGB;

    @Value("${libra.resources.limits.max-concurrent-jobs:10}")
    private int maxConcurrentJobs;

    @Value("${libra.resources.monitor.alert-threshold.cpu-percent:90}")
    private double cpuAlertThreshold;

    @Value("${libra.resources.monitor.alert-threshold.memory-percent:90}")
    private double memoryAlertThreshold;

    private final ConcurrentHashMap<String, ResourceUsage> activeJobs = new ConcurrentHashMap<>();

    @Autowired
    private MeterRegistry meterRegistry;

    @PostConstruct
    public void initialize() {
        // Register Prometheus gauges
        Gauge.builder("libra.resources.cpu.limit", () -> cpuLimitCores)
            .description("CPU cores limit from K8s")
            .register(meterRegistry);

        Gauge.builder("libra.resources.memory.limit", () -> memoryLimitGB)
            .description("Memory limit in GB from K8s")
            .register(meterRegistry);

        Gauge.builder("libra.resources.cpu.used", this::getCpuUsage)
            .description("Current CPU cores in use")
            .register(meterRegistry);

        Gauge.builder("libra.resources.memory.used", this::getMemoryUsage)
            .description("Current memory in GB in use")
            .register(meterRegistry);

        Gauge.builder("libra.resources.jobs.active", activeJobs::size)
            .description("Number of active jobs")
            .register(meterRegistry);

        Gauge.builder("libra.resources.cpu.utilization", this::getCpuUtilizationPercent)
            .description("CPU utilization percentage")
            .register(meterRegistry);

        Gauge.builder("libra.resources.memory.utilization", this::getMemoryUtilizationPercent)
            .description("Memory utilization percentage")
            .register(meterRegistry);

        log.info("ResourceMonitor initialized: CPU limit={} cores, Memory limit={} GB, Max jobs={}",
            cpuLimitCores, memoryLimitGB, maxConcurrentJobs);
    }

    /**
     * Check if resources can accommodate a new job
     *
     * @param requirement resource requirement
     * @return true if resources available
     */
    public boolean canAccommodate(ResourceRequirement requirement) {
        ResourceUsage current = calculateCurrentUsage();

        boolean cpuAvailable = (current.getCpuCores() + requirement.getTotalCores()) <= cpuLimitCores;
        boolean memoryAvailable = (current.getMemoryGB() + requirement.getTotalMemoryGB()) <= memoryLimitGB;
        boolean slotsAvailable = activeJobs.size() < maxConcurrentJobs;

        boolean canAccommodate = cpuAvailable && memoryAvailable && slotsAvailable;

        log.debug("Resource check for job: required={} cores, {} GB | available={}, reason: cpu={}, memory={}, slots={}",
            requirement.getTotalCores(), requirement.getTotalMemoryGB(), canAccommodate,
            cpuAvailable, memoryAvailable, slotsAvailable);

        return canAccommodate;
    }

    /**
     * Reserve resources for a job
     *
     * @param jobId job identifier
     * @param requirement resource requirement
     */
    public void reserveResources(String jobId, ResourceRequirement requirement) {
        ResourceUsage usage = new ResourceUsage(requirement);
        activeJobs.put(jobId, usage);

        log.info("Reserved resources for job {}: {} cores, {:.2f} GB",
            jobId, usage.getCpuCores(), usage.getMemoryGB());

        logResourceStatus();
    }

    /**
     * Release resources for a completed job
     *
     * @param jobId job identifier
     */
    public void releaseResources(String jobId) {
        ResourceUsage released = activeJobs.remove(jobId);

        if (released != null) {
            log.info("Released resources for job {}: {} cores, {:.2f} GB",
                jobId, released.getCpuCores(), released.getMemoryGB());

            logResourceStatus();
        } else {
            log.warn("Attempted to release resources for unknown job: {}", jobId);
        }
    }

    /**
     * Calculate current total resource usage
     */
    private ResourceUsage calculateCurrentUsage() {
        return activeJobs.values().stream()
            .reduce(ResourceUsage.ZERO, ResourceUsage::add);
    }

    private double getCpuUsage() {
        return calculateCurrentUsage().getCpuCores();
    }

    private double getMemoryUsage() {
        return calculateCurrentUsage().getMemoryGB();
    }

    private double getCpuUtilizationPercent() {
        return cpuLimitCores > 0 ? (getCpuUsage() / cpuLimitCores) * 100.0 : 0.0;
    }

    private double getMemoryUtilizationPercent() {
        return memoryLimitGB > 0 ? (getMemoryUsage() / memoryLimitGB) * 100.0 : 0.0;
    }

    private void logResourceStatus() {
        ResourceUsage current = calculateCurrentUsage();

        double cpuPercent = getCpuUtilizationPercent();
        double memoryPercent = getMemoryUtilizationPercent();

        log.info("Resource status: CPU {}/{} cores ({:.1f}%), Memory {:.1f}/{:.1f} GB ({:.1f}%), Jobs {}/{}",
            current.getCpuCores(), cpuLimitCores, cpuPercent,
            current.getMemoryGB(), memoryLimitGB, memoryPercent,
            activeJobs.size(), maxConcurrentJobs);
    }

    /**
     * Periodic resource monitoring and alerting
     */
    @Scheduled(fixedRateString = "${libra.resources.monitor.poll-interval:5000}")
    public void monitorResources() {
        double cpuPercent = getCpuUtilizationPercent();
        double memoryPercent = getMemoryUtilizationPercent();

        // Alert if usage exceeds thresholds
        if (cpuPercent > cpuAlertThreshold) {
            log.warn("HIGH CPU USAGE: {:.1f}% (threshold: {:.1f}%)", cpuPercent, cpuAlertThreshold);
        }

        if (memoryPercent > memoryAlertThreshold) {
            log.warn("HIGH MEMORY USAGE: {:.1f}% (threshold: {:.1f}%)", memoryPercent, memoryAlertThreshold);
        }

        // Log current active jobs
        if (!activeJobs.isEmpty() && log.isDebugEnabled()) {
            log.debug("Active jobs: {}", activeJobs.keySet());
        }
    }

    /**
     * Get current resource availability summary
     */
    public String getResourceSummary() {
        ResourceUsage current = calculateCurrentUsage();
        return String.format("CPU: %d/%d cores, Memory: %.1f/%.1f GB, Jobs: %d/%d",
            current.getCpuCores(), cpuLimitCores,
            current.getMemoryGB(), memoryLimitGB,
            activeJobs.size(), maxConcurrentJobs);
    }
}
