package com.mprs.reconciliation.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates the expected payout for a given transaction amount.
 *
 * Formula:
 *   expected = amount - (amount * feeRate) - fixedFee
 *
 * Example with defaults (feeRate=2.5%, fixedFee=$0.30):
 *   amount = $100.00
 *   expected = 100 - (100 * 0.025) - 0.30
 *            = 100 - 2.50 - 0.30
 *            = $97.20
 *
 * Fee configuration is externalized in application.yml
 * and overridable per environment via env variables.
 *
 * Uses BigDecimal throughout — never double/float for money.
 * Rounds to 2 decimal places using HALF_UP (standard financial rounding).
 */
@Slf4j
@Component
public class PayoutCalculator {

    private final BigDecimal feeRate;
    private final BigDecimal fixedFee;

    public PayoutCalculator(
            @Value("${mprs.fee.rate}") BigDecimal feeRate,
            @Value("${mprs.fee.fixed}") BigDecimal fixedFee) {
        this.feeRate  = feeRate;
        this.fixedFee = fixedFee;
        log.info("PayoutCalculator initialised — feeRate: {}%, fixedFee: ${}",
                feeRate.multiply(BigDecimal.valueOf(100)), fixedFee);
    }

    /**
     * Calculates the expected payout for a transaction amount.
     *
     * @param transactionAmount the gross transaction amount
     * @return expected net payout, rounded to 2 decimal places
     */
    public BigDecimal calculate(BigDecimal transactionAmount) {
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be positive, got: " + transactionAmount);
        }

        // Step 1: calculate percentage fee
        BigDecimal percentageFee = transactionAmount
                .multiply(feeRate)
                .setScale(4, RoundingMode.HALF_UP);

        // Step 2: subtract both fees from amount
        BigDecimal expected = transactionAmount
                .subtract(percentageFee)
                .subtract(fixedFee)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Calculated payout: {} - {} (fee) - {} (fixed) = {}",
                transactionAmount, percentageFee, fixedFee, expected);

        return expected;
    }

    /**
     * Checks if the variance between paid and expected is within tolerance.
     * Tolerance handles floating point rounding differences (±$0.01).
     *
     * @param expected expected payout
     * @param paid     actual payout
     * @return true if difference is within 1 cent
     */
    public boolean isWithinTolerance(BigDecimal expected, BigDecimal paid) {
        if (expected == null || paid == null) return false;
        BigDecimal variance = paid.subtract(expected).abs();
        return variance.compareTo(new BigDecimal("0.01")) <= 0;
    }
}