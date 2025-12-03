package com.whereq.libra.model;

/**
 * Job lifecycle states
 *
 * State transitions:
 * SUBMITTED → VALIDATING → QUEUED → SCHEDULED → RUNNING → {SUCCEEDED, FAILED, CANCELLED, TIMEOUT}
 * FAILED → RETRYING → QUEUED (if retry_count < max_retries)
 */
public enum JobStatus {
    /**
     * Job received via API
     */
    SUBMITTED,

    /**
     * Checking payload schema, auth, quotas
     */
    VALIDATING,

    /**
     * Waiting for resources
     */
    QUEUED,

    /**
     * Resources allocated, preparing execution
     */
    SCHEDULED,

    /**
     * Job actively executing
     */
    RUNNING,

    /**
     * Completed successfully
     */
    SUCCEEDED,

    /**
     * Terminated with error
     */
    FAILED,

    /**
     * User-initiated cancellation
     */
    CANCELLED,

    /**
     * Exceeded max execution time
     */
    TIMEOUT,

    /**
     * Re-attempting after failure
     */
    RETRYING;

    /**
     * Check if this is a terminal state
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * Check if job is currently running
     */
    public boolean isActive() {
        return this == RUNNING || this == SCHEDULED || this == QUEUED || this == VALIDATING;
    }
}
