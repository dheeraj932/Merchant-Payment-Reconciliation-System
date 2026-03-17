package com.mprs.reconciliation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity representing a reconciliation job.
 * Maps to the 'reconciliation_jobs' table.
 *
 * One job = one reconciliation run for a specific date window.
 * The unique constraint on (start_date, end_date) ensures
 * the same window cannot be reconciled twice — idempotency.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reconciliation_jobs")
public class ReconciliationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID assigned at job creation — used as the public job identifier. */
    @Column(name = "job_id", nullable = false, unique = true, length = 100)
    private String jobId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    /** Username of the user who triggered this job. */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Populated when job reaches COMPLETED or FAILED. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Populated when job reaches FAILED status. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}