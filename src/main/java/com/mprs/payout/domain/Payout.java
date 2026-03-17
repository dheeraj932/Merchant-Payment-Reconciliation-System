package com.mprs.payout.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity representing a payout made to a merchant.
 * Maps to the 'payouts' table created by Flyway migration V1.
 *
 * Each payout links to exactly one transaction via transaction_id.
 * The UNIQUE constraint on transaction_id in the DB enforces
 * that a transaction can only be paid once.
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
     * References the transaction this payout is for.
     * Kept as a plain string to avoid tight coupling between modules.
     * The reconciliation engine does the joining logic explicitly.
     */
    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    /**
     * Actual amount paid out to the merchant.
     * May differ from expected — difference is the variance.
     */
    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal payoutAmount;

    /**
     * Date the payout was transferred to the merchant.
     * Used as the primary filter for reconciliation windows.
     */
    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Payout(String transactionId, BigDecimal payoutAmount, LocalDate payoutDate) {
        this.transactionId = transactionId;
        this.payoutAmount  = payoutAmount;
        this.payoutDate    = payoutDate;
    }
}