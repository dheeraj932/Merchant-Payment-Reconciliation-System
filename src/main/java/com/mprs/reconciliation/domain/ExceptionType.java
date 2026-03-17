package com.mprs.reconciliation.domain;

/**
 * Classifies the type of discrepancy found during reconciliation.
 * A null exception type means the transaction was fully matched — no issue.
 */
public enum ExceptionType {

    /** Settled transaction exists but no payout record found. */
    MISSING_PAYOUT,

    /** Payout amount is less than the expected payout amount. */
    PARTIAL_PAYOUT,

    /** Payout amount exceeds the expected payout amount. */
    OVERPAYMENT,

    /** Multiple payout records exist for the same transaction_id. */
    DUPLICATE_PAYOUT,

    /**
     * Payout exists for a transaction that is not eligible
     * (status is REFUNDED or CHARGEBACK, not SETTLED).
     */
    INELIGIBLE_PAYOUT
}