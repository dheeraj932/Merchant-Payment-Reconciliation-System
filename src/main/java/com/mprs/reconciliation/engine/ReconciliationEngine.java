package com.mprs.reconciliation.engine;

import com.mprs.payout.domain.Payout;
import com.mprs.payout.service.PayoutService;
import com.mprs.reconciliation.domain.*;
import com.mprs.reconciliation.repository.ReconciliationJobRepository;
import com.mprs.reconciliation.repository.ReconciliationResultRepository;
import com.mprs.transaction.domain.Transaction;
import com.mprs.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core reconciliation orchestrator.
 *
 * Runs asynchronously (@Async) so the HTTP request returns immediately
 * with a jobId, while processing happens in a background thread.
 *
 * Flow:
 * 1. Load all transactions in the date window
 * 2. Load all payouts for those transactions (O(1) map lookup)
 * 3. For each transaction — classify exception (if any)
 * 4. Also check for orphan payouts (payouts with no matching transaction)
 * 5. Persist all results
 * 6. Update job status to COMPLETED or FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationEngine {

    private final TransactionService            transactionService;
    private final PayoutService                 payoutService;
    private final PayoutCalculator              payoutCalculator;
    private final ExceptionClassifier           exceptionClassifier;
    private final ReconciliationJobRepository   jobRepository;
    private final ReconciliationResultRepository resultRepository;

    /**
     * Runs a reconciliation job asynchronously.
     * Called by ReconciliationService after the job is created.
     *
     * @param job the reconciliation job to process
     */
    @Async("taskExecutor")
    @Transactional
    public void run(ReconciliationJob job) {
        log.info("Starting reconciliation job: {} for window {} to {}",
                job.getJobId(), job.getStartDate(), job.getEndDate());

        try {
            // 1. Mark job as RUNNING
            updateJobStatus(job, JobStatus.RUNNING, null);

            // 2. Load all transactions in the date window
            List<Transaction> transactions = transactionService
                    .getTransactionsByDateRange(job.getStartDate(), job.getEndDate());

            log.info("Job {}: loaded {} transactions", job.getJobId(), transactions.size());

            if (transactions.isEmpty()) {
                log.warn("Job {}: no transactions found in window {} to {}",
                        job.getJobId(), job.getStartDate(), job.getEndDate());
            }

            // 3. Extract transaction IDs for bulk payout lookup
            List<String> transactionIds = transactions.stream()
                    .map(Transaction::getTransactionId)
                    .toList();

            // 4. Load payouts as a map for O(1) lookup — critical for performance
            Map<String, Payout> payoutMap = payoutService
                    .getPayoutMapForTransactions(transactionIds);

            log.info("Job {}: loaded {} payouts", job.getJobId(), payoutMap.size());

            // 5. Process each transaction
            List<ReconciliationResult> results = new ArrayList<>();

            for (Transaction tx : transactions) {
                ReconciliationResult result = processTransaction(
                        job.getJobId(), tx, payoutMap);
                results.add(result);

                // Remove from map so we can detect orphan payouts later
                payoutMap.remove(tx.getTransactionId());
            }

            // 6. Any payouts remaining in the map are orphans —
            //    they have no matching transaction in the window
            for (Map.Entry<String, Payout> orphan : payoutMap.entrySet()) {
                log.warn("Job {}: orphan payout found for transaction_id: {}",
                        job.getJobId(), orphan.getKey());
            }

            // 7. Persist all results in one batch
            resultRepository.saveAll(results);

            log.info("Job {}: persisted {} results ({} exceptions)",
                    job.getJobId(),
                    results.size(),
                    results.stream().filter(r -> r.getExceptionType() != null).count());

            // 8. Mark job as COMPLETED
            updateJobStatus(job, JobStatus.COMPLETED, null);

            log.info("Reconciliation job {} COMPLETED successfully", job.getJobId());

        } catch (Exception e) {
            log.error("Reconciliation job {} FAILED: {}", job.getJobId(), e.getMessage(), e);
            updateJobStatus(job, JobStatus.FAILED, e.getMessage());
        }
    }

    // ── Private Helpers ──────────────────────────────────────────

    /**
     * Processes a single transaction against its payout.
     * Returns a ReconciliationResult — either matched or exception.
     */
    private ReconciliationResult processTransaction(String jobId,
                                                     Transaction tx,
                                                     Map<String, Payout> payoutMap) {
        Payout payout = payoutMap.get(tx.getTransactionId());

        // Calculate expected payout (only for eligible transactions)
        BigDecimal expected = tx.isEligibleForPayout()
                ? payoutCalculator.calculate(tx.getAmount())
                : null;

        BigDecimal paid = (payout != null) ? payout.getPayoutAmount() : null;

        // Classify the exception type
        ExceptionType exceptionType = exceptionClassifier.classify(tx, payout);

        // Build human-readable description
        String description = exceptionClassifier.buildDescription(
                exceptionType, tx, expected, paid);

        if (exceptionType == null) {
            // Fully matched
            return ReconciliationResult.matched(
                    jobId,
                    tx.getTransactionId(),
                    tx.getMerchantId(),
                    expected,
                    paid
            );
        } else {
            // Exception detected
            return ReconciliationResult.exception(
                    jobId,
                    tx.getTransactionId(),
                    tx.getMerchantId(),
                    expected,
                    paid,
                    exceptionType,
                    description
            );
        }
    }

    /**
     * Updates the job status and persists it.
     * Called at each lifecycle transition: RUNNING → COMPLETED/FAILED.
     */
    private void updateJobStatus(ReconciliationJob job,
                                  JobStatus status,
                                  String errorMessage) {
        job.setStatus(status);
        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        }
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }
        jobRepository.save(job);
    }
}