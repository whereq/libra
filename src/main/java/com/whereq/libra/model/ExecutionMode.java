package com.whereq.libra.model;

/**
 * Execution mode for Spark jobs
 */
public enum ExecutionMode {
    /**
     * Execute in Libra's JVM with resource limits (fast, shared)
     */
    IN_CLUSTER,

    /**
     * Launch via spark-submit (medium isolation)
     */
    SPARK_SUBMIT,

    /**
     * Create SparkApplication CRD via Operator (high isolation)
     */
    KUBERNETES,

    /**
     * Let Libra automatically select based on resource requirements
     */
    AUTO
}
