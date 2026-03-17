package com.mprs.reconciliation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing the reconciliation result for one transaction.
 * Maps to the 'reconciliation_results' table.
 *
 * One row per transaction per reconciliation job.
 * exceptionType is null when the transaction matched perfectly.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reconciliation_results")
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** References the parent reconciliation job. */
    @Column(name = "job_id", nullable = false, length = 100)
    private String jobId;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    /** Calculated expected payout: amount - fee% - fixed fee. */
    @Column(name = "expected_amount", precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    /** Actual payout amount. Null if no payout was found. */
    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount;

    /** paid_amount - expected_amount. Negative = underpaid, positive = overpaid. */
    @Column(name = "variance", precision = 19, scale = 4)
    private BigDecimal variance;

    /** Null means MATCHED — no discrepancy found. */
    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", length = 50)
    private ExceptionType exceptionType;

    /** Human-readable description of the exception. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Convenience factory method — builds a matched result (no exception).
     */
    public static ReconciliationResult matched(String jobId, String transactionId,
                                                String merchantId, BigDecimal expected,
                                                BigDecimal paid) {
        ReconciliationResult r = new ReconciliationResult();
        r.jobId          = jobId;
        r.transactionId  = transactionId;
        r.merchantId     = merchantId;
        r.expectedAmount = expected;
        r.paidAmount     = paid;
        r.variance       = paid.subtract(expected);
        r.exceptionType  = null;
        r.description    = "Transaction matched successfully";
        return r;
    }

    /**
     * Convenience factory method — builds an exception result.
     */
    public static ReconciliationResult exception(String jobId, String transactionId,
                                                  String merchantId, BigDecimal expected,
                                                  BigDecimal paid, ExceptionType type,
                                                  String description) {
        ReconciliationResult r = new ReconciliationResult();
        r.jobId          = jobId;
        r.transactionId  = transactionId;
        r.merchantId     = merchantId;
        r.expectedAmount = expected;
        r.paidAmount     = paid;
        r.variance       = (paid != null && expected != null)
                            ? paid.subtract(expected)
                            : null;
        r.exceptionType  = type;
        r.description    = description;
        return r;
    }
}