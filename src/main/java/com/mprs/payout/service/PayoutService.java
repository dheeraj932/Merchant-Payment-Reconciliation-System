package com.mprs.payout.service;

import com.mprs.payout.domain.Payout;
import com.mprs.payout.repository.PayoutRepository;
import com.mprs.shared.exception.DuplicateResourceException;
import com.mprs.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Business logic for payout ingestion and querying.
 *
 * Key difference from TransactionService:
 * Duplicate payouts are a financial error — they are flagged
 * in errors (not silently skipped) because paying a merchant
 * twice is a critical discrepancy that must be surfaced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;

    // ── Ingestion ────────────────────────────────────────────────

    /**
     * Bulk ingests a list of payout records.
     *
     * Processing rules:
     * - Validates each record before persisting
     * - Flags duplicate transaction_ids as errors (not silent skips)
     * - Saves all valid, non-duplicate records in one batch
     *
     * @param requests list of payout data to ingest
     * @return ingestion result summary
     */
    @Transactional
    public BulkIngestionResult ingestPayouts(List<PayoutRequest> requests) {
        log.info("Starting bulk ingestion of {} payouts", requests.size());

        List<Payout>  toSave  = new ArrayList<>();
        List<String>  skipped = new ArrayList<>();
        List<String>  errors  = new ArrayList<>();

        for (PayoutRequest req : requests) {
            try {
                // 1. Validate business rules
                validatePayoutRequest(req);

                // 2. Check for duplicates — flag as error, not silent skip
                if (payoutRepository.existsByTransactionId(req.transactionId())) {
                    log.warn("Duplicate payout detected for transaction: {}",
                            req.transactionId());
                    errors.add(req.transactionId()
                            + ": duplicate payout — transaction already has a payout record");
                    continue;
                }

                // 3. Map to entity
                toSave.add(new Payout(
                        req.transactionId(),
                        req.payoutAmount(),
                        req.payoutDate()
                ));

            } catch (ValidationException e) {
                log.warn("Validation error for payout {}: {}",
                        req.transactionId(), e.getMessage());
                errors.add(req.transactionId() + ": " + e.getMessage());
            }
        }

        // 4. Batch save all valid payouts
        if (!toSave.isEmpty()) {
            payoutRepository.saveAll(toSave);
            log.info("Saved {} payouts, skipped {}, errors {}",
                    toSave.size(), skipped.size(), errors.size());
        }

        return new BulkIngestionResult(toSave.size(), skipped.size(), errors);
    }

    // ── Queries ──────────────────────────────────────────────────

    /**
     * Returns all payouts within a payout date window.
     * Used by the reconciliation engine.
     */
    @Transactional(readOnly = true)
    public List<Payout> getPayoutsByDateRange(LocalDate start, LocalDate end) {
        log.debug("Fetching payouts between {} and {}", start, end);
        return payoutRepository.findByPayoutDateBetween(start, end);
    }

    /**
     * Returns payouts as a map keyed by transaction_id.
     * Used by reconciliation engine for O(1) lookup during matching.
     *
     * @param transactionIds list of transaction IDs to look up
     * @return map of transactionId → Payout
     */
    @Transactional(readOnly = true)
    public Map<String, Payout> getPayoutMapForTransactions(List<String> transactionIds) {
        return payoutRepository.findByTransactionIdIn(transactionIds)
                .stream()
                .collect(Collectors.toMap(
                        Payout::getTransactionId,
                        payout -> payout
                ));
    }

    /**
     * Returns all payouts (for GET /api/v1/payouts).
     */
    @Transactional(readOnly = true)
    public List<Payout> getAllPayouts() {
        return payoutRepository.findAll();
    }

    /**
     * Finds a single payout by transaction ID.
     */
    @Transactional(readOnly = true)
    public Optional<Payout> getPayoutByTransactionId(String transactionId) {
        return payoutRepository.findByTransactionId(transactionId);
    }

    // ── Validation ───────────────────────────────────────────────

    /**
     * Validates a single payout request against business rules.
     */
    private void validatePayoutRequest(PayoutRequest req) {
        if (req.transactionId() == null || req.transactionId().isBlank()) {
            throw new ValidationException("transaction_id is required");
        }
        if (req.payoutAmount() == null
                || req.payoutAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "payout_amount must be greater than zero for transaction: "
                            + req.transactionId());
        }
        if (req.payoutDate() == null) {
            throw new ValidationException(
                    "payout_date is required for transaction: " + req.transactionId());
        }
    }

    // ── Inner DTOs ───────────────────────────────────────────────

    /**
     * Inbound data for a single payout record.
     */
    public record PayoutRequest(
            String     transactionId,
            BigDecimal payoutAmount,
            LocalDate  payoutDate
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