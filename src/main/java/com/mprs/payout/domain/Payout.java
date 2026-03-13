package com.mprs.payout.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a payout record.
 * Maps to the 'payouts' table defined in the initial Flyway migration.
 *
 * Business key: transactionId (unique per transaction).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identifier of the upstream transaction this payout belongs to.
     * Unique per transaction — duplicate payouts are rejected at ingestion time.
     */
    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    /**
     * Amount paid out to the merchant.
     */
    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal payoutAmount;

    /**
     * Payout date stored as ISO-8601 string (YYYY-MM-DD).
     * The underlying column is VARCHAR; reconciliation uses lexical range filtering.
     */
    @Column(name = "payout_date", nullable = false, length = 50)
    private String payoutDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Payout(String transactionId, BigDecimal payoutAmount, String payoutDate) {
        this.transactionId = transactionId;
        this.payoutAmount = payoutAmount;
        this.payoutDate = payoutDate;
    }
}

