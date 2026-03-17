package com.mprs.reconciliation.service;

import com.mprs.reconciliation.domain.*;
import com.mprs.reconciliation.engine.ReconciliationEngine;
import com.mprs.reconciliation.repository.ReconciliationJobRepository;
import com.mprs.reconciliation.repository.ReconciliationResultRepository;
import com.mprs.shared.exception.DuplicateResourceException;
import com.mprs.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates reconciliation job lifecycle.
 *
 * Responsibilities:
 * - Create and persist reconciliation jobs
 * - Enforce idempotency (same date window cannot run twice)
 * - Delegate async processing to ReconciliationEngine
 * - Serve job status, summary reports, and exception lists
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final ReconciliationJobRepository    jobRepository;
    private final ReconciliationResultRepository resultRepository;
    private final ReconciliationEngine           reconciliationEngine;

    // ── Job Triggering ───────────────────────────────────────────

    /**
     * Creates a new reconciliation job and triggers async processing.
     *
     * Idempotency: if a job already exists for the same date window,
     * throws DuplicateResourceException (HTTP 409).
     *
     * @param startDate   start of reconciliation window (inclusive)
     * @param endDate     end of reconciliation window (inclusive)
     * @param triggeredBy username of the user triggering the job
     * @return the created ReconciliationJob (status = PENDING)
     */
    @Transactional
    public ReconciliationJob triggerReconciliation(LocalDate startDate,
                                                    LocalDate endDate,
                                                    String triggeredBy) {
        log.info("Reconciliation triggered by {} for window {} to {}",
                triggeredBy, startDate, endDate);

        // Validate date range
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "end_date must be on or after start_date");
        }

        // Idempotency check — same window cannot run twice
        if (jobRepository.existsByStartDateAndEndDate(startDate, endDate)) {
            throw new DuplicateResourceException(
                    "Reconciliation job already exists for window: "
                            + startDate + " to " + endDate);
        }

        // Create job with PENDING status
        ReconciliationJob job = new ReconciliationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setStartDate(startDate);
        job.setEndDate(endDate);
        job.setStatus(JobStatus.PENDING);
        job.setTriggeredBy(triggeredBy);

        jobRepository.save(job);

        log.info("Created reconciliation job: {}", job.getJobId());

        // Trigger async processing — returns immediately
        reconciliationEngine.run(job);

        return job;
    }

    // ── Job Queries ──────────────────────────────────────────────

    /**
     * Returns the current status of a reconciliation job.
     *
     * @param jobId the public UUID of the job
     * @return the ReconciliationJob
     * @throws ResourceNotFoundException if job not found
     */
    @Transactional(readOnly = true)
    public ReconciliationJob getJob(String jobId) {
        return jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ReconciliationJob", "jobId", jobId));
    }

    // ── Summary Report ───────────────────────────────────────────

    /**
     * Generates a summary report for a completed reconciliation job.
     *
     * Summary includes:
     * - Total eligible transactions
     * - Total expected payout
     * - Total paid
     * - Total variance
     * - Count by exception type
     *
     * @param jobId the public UUID of the job
     * @return ReconciliationSummary DTO
     */
    @Transactional(readOnly = true)
    public ReconciliationSummary getSummary(String jobId) {
        // Verify job exists
        ReconciliationJob job = getJob(jobId);

        List<ReconciliationResult> results = resultRepository.findByJobId(jobId);

        if (results.isEmpty()) {
            return ReconciliationSummary.empty(jobId, job.getStatus().name());
        }

        // Aggregate totals
        BigDecimal totalExpected = results.stream()
                .filter(r -> r.getExpectedAmount() != null)
                .map(ReconciliationResult::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = results.stream()
                .filter(r -> r.getPaidAmount() != null)
                .map(ReconciliationResult::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVariance = totalPaid.subtract(totalExpected);

        long totalTransactions = results.size();
        long totalExceptions   = results.stream()
                .filter(r -> r.getExceptionType() != null)
                .count();
        long totalMatched      = totalTransactions - totalExceptions;

        // Count by exception type
        Map<String, Long> exceptionCounts = new HashMap<>();
        for (ExceptionType type : ExceptionType.values()) {
            long count = results.stream()
                    .filter(r -> type.equals(r.getExceptionType()))
                    .count();
            if (count > 0) exceptionCounts.put(type.name(), count);
        }

        return new ReconciliationSummary(
                jobId,
                job.getStatus().name(),
                job.getStartDate(),
                job.getEndDate(),
                totalTransactions,
                totalMatched,
                totalExceptions,
                totalExpected,
                totalPaid,
                totalVariance,
                exceptionCounts
        );
    }

    // ── Exception Report ─────────────────────────────────────────

    /**
     * Returns the detailed exception list for a reconciliation job.
     * Only returns rows where exceptionType IS NOT NULL.
     *
     * @param jobId         the public UUID of the job
     * @param exceptionType optional filter by exception type
     * @return list of ExceptionDetail DTOs
     */
    @Transactional(readOnly = true)
    public List<ExceptionDetail> getExceptions(String jobId, ExceptionType exceptionType) {
        // Verify job exists
        getJob(jobId);

        List<ReconciliationResult> results = (exceptionType != null)
                ? resultRepository.findByJobIdAndExceptionType(jobId, exceptionType)
                : resultRepository.findExceptionsByJobId(jobId);

        return results.stream()
                .map(r -> new ExceptionDetail(
                        r.getTransactionId(),
                        r.getMerchantId(),
                        r.getExpectedAmount(),
                        r.getPaidAmount(),
                        r.getVariance(),
                        r.getExceptionType() != null ? r.getExceptionType().name() : null,
                        r.getDescription()
                ))
                .toList();
    }

    // ── Inner DTOs ───────────────────────────────────────────────

    /**
     * Summary report DTO returned by GET /reconciliation/{jobId}/summary
     */
    public record ReconciliationSummary(
            String              jobId,
            String              jobStatus,
            LocalDate           startDate,
            LocalDate           endDate,
            long                totalTransactions,
            long                totalMatched,
            long                totalExceptions,
            BigDecimal          totalExpectedPayout,
            BigDecimal          totalPaidPayout,
            BigDecimal          totalVariance,
            Map<String, Long>   exceptionCounts
    ) {
        public static ReconciliationSummary empty(String jobId, String status) {
            return new ReconciliationSummary(jobId, status,
                    null, null, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    Map.of());
        }
    }

    /**
     * Exception detail DTO returned by GET /reconciliation/{jobId}/exceptions
     */
    public record ExceptionDetail(
            String     transactionId,
            String     merchantId,
            BigDecimal expectedAmount,
            BigDecimal paidAmount,
            BigDecimal variance,
            String     exceptionType,
            String     description
    ) {}
}