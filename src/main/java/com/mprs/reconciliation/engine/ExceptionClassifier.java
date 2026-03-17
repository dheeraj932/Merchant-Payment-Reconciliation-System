package com.mprs.reconciliation.engine;

import com.mprs.payout.domain.Payout;
import com.mprs.reconciliation.domain.ExceptionType;
import com.mprs.transaction.domain.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Classifies discrepancies between a transaction and its payout.
 *
 * Classification rules (applied in order):
 * 1. Ineligible payout  — payout exists for non-SETTLED transaction
 * 2. Missing payout     — no payout found for SETTLED transaction
 * 3. Duplicate payout   — flagged upstream, passed through here
 * 4. Partial payout     — paid < expected (outside tolerance)
 * 5. Overpayment        — paid > expected (outside tolerance)
 * 6. null (matched)     — paid == expected (within tolerance)
 */
@Slf4j
@Component
public class ExceptionClassifier {

    private final PayoutCalculator payoutCalculator;

    public ExceptionClassifier(PayoutCalculator payoutCalculator) {
        this.payoutCalculator = payoutCalculator;
    }

    /**
     * Classifies the exception type for a transaction/payout pair.
     *
     * @param transaction the transaction being reconciled
     * @param payout      the payout for this transaction (null if missing)
     * @return ExceptionType, or null if fully matched
     */
    public ExceptionType classify(Transaction transaction, Payout payout) {

        // Rule 1: Ineligible payout — payout exists but transaction not eligible
        if (payout != null && !transaction.isEligibleForPayout()) {
            log.debug("INELIGIBLE_PAYOUT: transaction {} has status {}",
                    transaction.getTransactionId(), transaction.getStatus());
            return ExceptionType.INELIGIBLE_PAYOUT;
        }

        // Rule 2: Missing payout — eligible transaction has no payout
        if (payout == null && transaction.isEligibleForPayout()) {
            log.debug("MISSING_PAYOUT: no payout found for transaction {}",
                    transaction.getTransactionId());
            return ExceptionType.MISSING_PAYOUT;
        }

        // Rule 3: Both null-eligible and no payout — not an exception
        // (ineligible transaction with no payout — expected, not an error)
        if (payout == null) {
            return null;
        }

        // Rules 4-6 require amount comparison
        BigDecimal expected = payoutCalculator.calculate(transaction.getAmount());
        BigDecimal paid     = payout.getPayoutAmount();

        // Rule 4 & 5: Amount mismatch outside tolerance
        if (!payoutCalculator.isWithinTolerance(expected, paid)) {
            int comparison = paid.compareTo(expected);
            if (comparison < 0) {
                log.debug("PARTIAL_PAYOUT: transaction {} expected {} paid {}",
                        transaction.getTransactionId(), expected, paid);
                return ExceptionType.PARTIAL_PAYOUT;
            } else {
                log.debug("OVERPAYMENT: transaction {} expected {} paid {}",
                        transaction.getTransactionId(), expected, paid);
                return ExceptionType.OVERPAYMENT;
            }
        }

        // Rule 6: Fully matched
        log.debug("MATCHED: transaction {} expected {} paid {}",
                transaction.getTransactionId(), expected, paid);
        return null;
    }

    /**
     * Builds a human-readable description for an exception.
     *
     * @param type        the exception type
     * @param transaction the transaction
     * @param expected    expected payout amount
     * @param paid        actual payout amount (null for missing)
     * @return description string for the reconciliation result
     */
    public String buildDescription(ExceptionType type, Transaction transaction,
                                   BigDecimal expected, BigDecimal paid) {
        if (type == null) return "Transaction matched successfully";

        return switch (type) {
            case MISSING_PAYOUT -> String.format(
                    "No payout found for settled transaction %s (expected: $%s)",
                    transaction.getTransactionId(), expected);

            case PARTIAL_PAYOUT -> String.format(
                    "Partial payout for transaction %s — expected: $%s, paid: $%s, short by: $%s",
                    transaction.getTransactionId(), expected, paid,
                    expected.subtract(paid));

            case OVERPAYMENT -> String.format(
                    "Overpayment for transaction %s — expected: $%s, paid: $%s, excess: $%s",
                    transaction.getTransactionId(), expected, paid,
                    paid.subtract(expected));

            case DUPLICATE_PAYOUT -> String.format(
                    "Duplicate payout detected for transaction %s",
                    transaction.getTransactionId());

            case INELIGIBLE_PAYOUT -> String.format(
                    "Payout exists for ineligible transaction %s (status: %s)",
                    transaction.getTransactionId(), transaction.getStatus());
        };
    }
}