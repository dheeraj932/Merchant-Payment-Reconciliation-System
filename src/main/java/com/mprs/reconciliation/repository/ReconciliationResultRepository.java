package com.mprs.reconciliation.repository;

import com.mprs.reconciliation.domain.ExceptionType;
import com.mprs.reconciliation.domain.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for reconciliation results.
 */
@Repository
public interface ReconciliationResultRepository
        extends JpaRepository<ReconciliationResult, Long> {

    /**
     * Returns all results for a given job.
     * Used by the summary and exception report endpoints.
     */
    List<ReconciliationResult> findByJobId(String jobId);

    /**
     * Returns only exception results for a given job.
     * exceptionType IS NOT NULL means a discrepancy was found.
     */
    @Query("""
            SELECT r FROM ReconciliationResult r
            WHERE r.jobId = :jobId
            AND   r.exceptionType IS NOT NULL
            ORDER BY r.exceptionType ASC, r.transactionId ASC
            """)
    List<ReconciliationResult> findExceptionsByJobId(@Param("jobId") String jobId);

    /**
     * Returns results filtered by a specific exception type.
     * Useful for finance analysts drilling into one category.
     */
    List<ReconciliationResult> findByJobIdAndExceptionType(
            String jobId, ExceptionType exceptionType);

    /**
     * Counts exceptions by type for the summary report.
     */
    @Query("""
            SELECT r.exceptionType, COUNT(r)
            FROM ReconciliationResult r
            WHERE r.jobId = :jobId
            AND   r.exceptionType IS NOT NULL
            GROUP BY r.exceptionType
            """)
    List<Object[]> countExceptionsByType(@Param("jobId") String jobId);
}