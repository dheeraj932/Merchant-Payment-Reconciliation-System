package com.mprs.transaction.domain;

/**
 * Represents the lifecycle status of a merchant transaction.
 *
 * Only SETTLED transactions are eligible for payout.
 * REFUNDED and CHARGEBACK transactions must never be paid out.
 */
public enum TransactionStatus {

    /** Transaction has been authorised but not yet settled. Not eligible for payout. */
    AUTHORIZED,

    /** Transaction has been fully settled. ELIGIBLE for payout. */
    SETTLED,

    /** Transaction has been refunded to the customer. NOT eligible for payout. */
    REFUNDED,

    /** Transaction has been disputed and charged back. NOT eligible for payout. */
    CHARGEBACK
}