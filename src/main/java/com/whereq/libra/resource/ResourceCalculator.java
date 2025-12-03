package com.whereq.libra.resource;

import com.whereq.libra.dto.SparkJobRequest;
import com.whereq.libra.model.ResourceRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculate resource requirements from Spark configuration
 */
@Slf4j
@Service
public class ResourceCalculator {

    private static final Pattern MEMORY_PATTERN = Pattern.compile("(\\d+)([gmk])?", Pattern.CASE_INSENSITIVE);

    /**
     * Calculate resource requirements from job request
     *
     * @param request the job request
     * @return calculated resource requirements
     */
    public ResourceRequirement calculate(SparkJobRequest request) {
        Map<String, String> sparkConfig = request.getSparkConfig();
        if (sparkConfig == null || sparkConfig.isEmpty()) {
            return defaultRequirement();
        }

        int executorCount = parseInt(sparkConfig.getOrDefault("spark.executor.instances", "2"));
        int executorCores = parseInt(sparkConfig.getOrDefault("spark.executor.cores", "2"));
        long executorMemoryMB = parseMemory(sparkConfig.getOrDefault("spark.executor.memory", "2g"));

        int driverCores = parseInt(sparkConfig.getOrDefault("spark.driver.cores", "1"));
        long driverMemoryMB = parseMemory(sparkConfig.getOrDefault("spark.driver.memory", "2g"));

        int totalCores = (executorCount * executorCores) + driverCores;
        long totalMemoryMB = (executorCount * executorMemoryMB) + driverMemoryMB;

        ResourceRequirement requirement = ResourceRequirement.builder()
            .executorCount(executorCount)
            .executorCores(executorCores)
            .executorMemoryMB(executorMemoryMB)
            .driverCores(driverCores)
            .driverMemoryMB(driverMemoryMB)
            .totalCores(totalCores)
            .totalMemoryMB(totalMemoryMB)
            .build();

        log.debug("Calculated resource requirement: {} executors, {} cores, {} MB memory",
            executorCount, totalCores, totalMemoryMB);

        return requirement;
    }

    /**
     * Parse memory string (e.g., "2g", "2048m", "2048000k") to MB
     *
     * @param memory memory string
     * @return memory in MB
     */
    private long parseMemory(String memory) {
        Matcher matcher = MEMORY_PATTERN.matcher(memory.toLowerCase());
        if (!matcher.matches()) {
            log.warn("Invalid memory format: {}, using default 2048m", memory);
            return 2048;
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        if (unit == null) {
            return value; // Assume MB if no unit
        }

        return switch (unit) {
            case "g" -> value * 1024;
            case "m" -> value;
            case "k" -> value / 1024;
            default -> value;
        };
    }

    /**
     * Parse integer with fallback to default
     */
    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer: {}, using default", value);
            return 2;
        }
    }

    /**
     * Default resource requirement (minimal)
     */
    private ResourceRequirement defaultRequirement() {
        return ResourceRequirement.builder()
            .executorCount(2)
            .executorCores(2)
            .executorMemoryMB(2048)
            .driverCores(1)
            .driverMemoryMB(2048)
            .totalCores(5)
            .totalMemoryMB(6144)
            .build();
    }
}
