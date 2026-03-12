package com.mprs.transaction.service;

import com.mprs.shared.exception.DuplicateResourceException;
import com.mprs.shared.exception.ValidationException;
import com.mprs.transaction.domain.Transaction;
import com.mprs.transaction.domain.TransactionStatus;
import com.mprs.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for transaction ingestion and querying.
 *
 * All database writes are wrapped in transactions (@Transactional).
 * Read operations use readOnly=true for performance optimisation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    // ── Ingestion ────────────────────────────────────────────────

    /**
     * Bulk ingests a list of transaction records.
     *
     * Processing rules:
     * - Validates each record before persisting
     * - Skips duplicates (already-existing transaction_ids) with a warning
     * - Saves all valid, non-duplicate records in one batch
     *
     * @param requests list of transaction data to ingest
     * @return ingestion result summary
     */
    @Transactional
    public BulkIngestionResult ingestTransactions(List<TransactionRequest> requests) {
        log.info("Starting bulk ingestion of {} transactions", requests.size());

        List<Transaction> toSave   = new ArrayList<>();
        List<String>      skipped  = new ArrayList<>();
        List<String>      errors   = new ArrayList<>();

        for (TransactionRequest req : requests) {
            try {
                // 1. Validate business rules
                validateTransactionRequest(req);

                // 2. Check for duplicates
                if (transactionRepository.existsByTransactionId(req.transactionId())) {
                    log.warn("Duplicate transaction skipped: {}", req.transactionId());
                    skipped.add(req.transactionId());
                    continue;
                }

                // 3. Map to entity
                toSave.add(new Transaction(
                        req.transactionId(),
                        req.amount(),
                        req.status(),
                        req.settlementDate(),
                        req.merchantId()
                ));

            } catch (ValidationException e) {
                log.warn("Validation error for transaction {}: {}",
                        req.transactionId(), e.getMessage());
                errors.add(req.transactionId() + ": " + e.getMessage());
            }
        }

        // 4. Batch save all valid transactions
        if (!toSave.isEmpty()) {
            transactionRepository.saveAll(toSave);
            log.info("Saved {} transactions, skipped {}, errors {}",
                    toSave.size(), skipped.size(), errors.size());
        }

        return new BulkIngestionResult(toSave.size(), skipped.size(), errors);
    }

    // ── Queries ──────────────────────────────────────────────────

    /**
     * Returns all transactions within a settlement date window.
     * Used by the reconciliation engine.
     */
    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByDateRange(LocalDate start, LocalDate end) {
        log.debug("Fetching transactions between {} and {}", start, end);
        return transactionRepository.findBySettlementDateBetween(start, end);
    }

    /**
     * Returns only SETTLED transactions within a date window.
     * These are the only transactions eligible for payout.
     */
    @Transactional(readOnly = true)
    public List<Transaction> getSettledTransactions(LocalDate start, LocalDate end) {
        return transactionRepository.findBySettlementDateBetweenAndStatus(
                start, end, TransactionStatus.SETTLED);
    }

    /**
     * Returns all transactions (for GET /api/v1/transactions).
     */
    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    // ── Validation ───────────────────────────────────────────────

    /**
     * Validates a single transaction request against business rules.
     * Throws ValidationException with a descriptive message on failure.
     */
    private void validateTransactionRequest(TransactionRequest req) {
        if (req.transactionId() == null || req.transactionId().isBlank()) {
            throw new ValidationException("transaction_id is required");
        }
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "amount must be greater than zero for transaction: " + req.transactionId());
        }
        if (req.status() == null) {
            throw new ValidationException(
                    "status is required for transaction: " + req.transactionId());
        }
        if (req.settlementDate() == null) {
            throw new ValidationException(
                    "settlement_date is required for transaction: " + req.transactionId());
        }
        if (req.merchantId() == null || req.merchantId().isBlank()) {
            throw new ValidationException(
                    "merchant_id is required for transaction: " + req.transactionId());
        }
    }

    // ── Inner DTOs (service-layer contracts) ─────────────────────

    /**
     * Inbound data for a single transaction.
     * Used internally between controller and service.
     */
    public record TransactionRequest(
            String            transactionId,
            BigDecimal        amount,
            TransactionStatus status,
            LocalDate         settlementDate,
            String            merchantId
    ) {}

    /**
     * Result summary returned after bulk ingestion.
     */
    public record BulkIngestionResult(
            int          saved,
            int          skipped,
            List<String> errors
    ) {}
}