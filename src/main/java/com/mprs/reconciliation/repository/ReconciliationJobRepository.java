package com.mprs.reconciliation.repository;

import com.mprs.reconciliation.domain.ReconciliationJob;
import com.mprs.reconciliation.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for reconciliation jobs.
 */
@Repository
public interface ReconciliationJobRepository extends JpaRepository<ReconciliationJob, Long> {

    /**
     * Finds a job by its public UUID.
     * Used by the API to look up job status and results.
     */
    Optional<ReconciliationJob> findByJobId(String jobId);

    /**
     * Checks if a job already exists for a given date window.
     * Used to enforce idempotency — same window cannot run twice.
     */
    boolean existsByStartDateAndEndDate(LocalDate startDate, LocalDate endDate);

    /**
     * Returns all jobs with a given status.
     * Useful for monitoring and ops dashboards.
     */
    List<ReconciliationJob> findByStatus(JobStatus status);
}