package com.mprs.payout.service;

import com.mprs.payout.domain.Payout;
import com.mprs.payout.repository.PayoutRepository;
import com.mprs.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for payout ingestion and querying.
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
     * Rules:
     * - Validates each record before persisting.
     * - Skips duplicates (existing transaction_id) with a warning.
     * - Saves all valid, non-duplicate records in one batch.
     */
    @Transactional
    public BulkIngestionResult ingestPayouts(List<PayoutRequest> requests) {
        log.info("Starting bulk ingestion of {} payouts", requests.size());

        List<Payout> toSave = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (PayoutRequest req : requests) {
            try {
                validatePayoutRequest(req);

                // Duplicate detection based on transaction_id (business key)
                if (payoutRepository.existsByTransactionId(req.transactionId())) {
                    log.warn("Duplicate payout skipped for transaction: {}", req.transactionId());
                    skipped.add(req.transactionId());
                    continue;
                }

                // Map to entity (dates stored as ISO-8601 strings)
                toSave.add(new Payout(
                        req.transactionId(),
                        req.payoutAmount(),
                        req.payoutDate().toString()
                ));

            } catch (ValidationException e) {
                log.warn("Validation error for payout {}: {}", req.transactionId(), e.getMessage());
                errors.add(req.transactionId() + ": " + e.getMessage());
            }
        }

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
     * Used by reconciliation engine.
     */
    @Transactional(readOnly = true)
    public List<Payout> getPayoutsByDateRange(LocalDate start, LocalDate end) {
        log.debug("Fetching payouts between {} and {}", start, end);
        return payoutRepository.findByPayoutDateBetween(start.toString(), end.toString());
    }

    /**
     * Returns all payouts.
     */
    @Transactional(readOnly = true)
    public List<Payout> getAllPayouts() {
        return payoutRepository.findAll();
    }

    // ── Validation ───────────────────────────────────────────────

    private void validatePayoutRequest(PayoutRequest req) {
        if (req.transactionId() == null || req.transactionId().isBlank()) {
            throw new ValidationException("transaction_id is required");
        }
        if (req.payoutAmount() == null || req.payoutAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "payout_amount must be greater than zero for transaction: " + req.transactionId());
        }
        if (req.payoutDate() == null) {
            throw new ValidationException(
                    "payout_date is required for transaction: " + req.transactionId());
        }
    }

    // ── Inner DTOs ───────────────────────────────────────────────

    /**
     * Inbound data for a single payout.
     */
    public record PayoutRequest(
            String transactionId,
            BigDecimal payoutAmount,
            LocalDate payoutDate
    ) {}

    /**
     * Result summary returned after bulk ingestion.
     */
    public record BulkIngestionResult(
            int saved,
            int skipped,
            List<String> errors
    ) {}
}

