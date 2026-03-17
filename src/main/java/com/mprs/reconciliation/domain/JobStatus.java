package com.mprs.reconciliation.domain;

/**
 * Represents the lifecycle status of a reconciliation job.
 *
 * State transitions:
 * PENDING → RUNNING → COMPLETED
 *                   → FAILED
 */
public enum JobStatus {

    /** Job has been created and is queued for processing. */
    PENDING,

    /** Job is actively running in a background thread. */
    RUNNING,

    /** Job completed successfully. Results are available. */
    COMPLETED,

    /** Job failed due to an unexpected error. Check error_message. */
    FAILED
}