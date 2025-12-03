package com.whereq.libra.service;

import com.whereq.libra.dto.SparkJobRequest;
import com.whereq.libra.executor.InClusterExecutor;
import com.whereq.libra.executor.JobExecutor;
import com.whereq.libra.model.ExecutionMode;
import com.whereq.libra.model.ResourceRequirement;
import com.whereq.libra.resource.ResourceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Factory for selecting appropriate job executor based on execution mode
 */
@Slf4j
@Service
public class ExecutionStrategyFactory {

    @Autowired
    private InClusterExecutor inClusterExecutor;

    @Autowired(required = false)
    private JarJobExecutor jarJobExecutor; // spark-submit executor

    @Autowired(required = false)
    private Object kubernetesOperatorExecutor; // Will implement in Phase 3

    @Autowired
    private ResourceCalculator resourceCalculator;

    @Value("${libra.execution.modes.in-cluster.enabled:true}")
    private boolean inClusterEnabled;

    @Value("${libra.execution.modes.spark-submit.enabled:true}")
    private boolean sparkSubmitEnabled;

    @Value("${libra.execution.modes.kubernetes.enabled:false}")
    private boolean kubernetesEnabled;

    /**
     * Select appropriate executor for the job
     *
     * @param request the job request
     * @return selected executor
     */
    public JobExecutor selectExecutor(SparkJobRequest request) {
        String mode = request.getExecutionMode();

        // Explicit mode selection
        if (mode != null && !mode.equalsIgnoreCase("auto")) {
            ExecutionMode executionMode = ExecutionMode.valueOf(mode.toUpperCase());
            return getExecutorByMode(executionMode);
        }

        // Auto-selection based on resource requirements
        ResourceRequirement req = resourceCalculator.calculate(request);
        ExecutionMode selectedMode = autoSelectMode(req);

        log.info("Auto-selected execution mode {} for job with {} executors, {:.1f} GB memory",
            selectedMode, req.getExecutorCount(), req.getTotalMemoryGB());

        return getExecutorByMode(selectedMode);
    }

    /**
     * Auto-select execution mode based on resource requirements
     */
    private ExecutionMode autoSelectMode(ResourceRequirement req) {
        // Large jobs → Kubernetes (if enabled)
        if (req.isLargeJob() && kubernetesEnabled) {
            return ExecutionMode.KUBERNETES;
        }

        // Medium jobs → spark-submit (if enabled)
        if (req.needsIsolation() && sparkSubmitEnabled) {
            return ExecutionMode.SPARK_SUBMIT;
        }

        // Small jobs → in-cluster (if enabled)
        if (inClusterEnabled) {
            return ExecutionMode.IN_CLUSTER;
        }

        // Fallback to spark-submit
        return ExecutionMode.SPARK_SUBMIT;
    }

    /**
     * Get executor instance by mode
     */
    private JobExecutor getExecutorByMode(ExecutionMode mode) {
        return switch (mode) {
            case IN_CLUSTER -> {
                if (!inClusterEnabled) {
                    throw new IllegalStateException("In-cluster execution mode is disabled");
                }
                yield inClusterExecutor;
            }
            case SPARK_SUBMIT -> {
                if (!sparkSubmitEnabled || jarJobExecutor == null) {
                    throw new IllegalStateException("Spark-submit execution mode is disabled or not configured");
                }
                yield (JobExecutor) jarJobExecutor;
            }
            case KUBERNETES -> {
                if (!kubernetesEnabled || kubernetesOperatorExecutor == null) {
                    throw new IllegalStateException("Kubernetes execution mode is disabled or not configured");
                }
                yield (JobExecutor) kubernetesOperatorExecutor;
            }
            default -> throw new IllegalArgumentException("Unsupported execution mode: " + mode);
        };
    }
}
