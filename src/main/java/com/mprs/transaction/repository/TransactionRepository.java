package com.mprs.transaction.repository;

import com.mprs.transaction.domain.Transaction;
import com.mprs.transaction.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for transactions.
 * Spring Data JPA generates all query implementations at runtime.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Finds a transaction by its business key.
     * Used for duplicate detection during bulk ingestion.
     */
    Optional<Transaction> findByTransactionId(String transactionId);

    /**
     * Checks if a transaction with the given business key already exists.
     * More efficient than findByTransactionId when you only need existence.
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Loads all transactions within a settlement date window.
     * Core query for the reconciliation engine — called with the job's date range.
     *
     * Uses JPQL to keep it database-agnostic (works with PostgreSQL and H2 for tests).
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.settlementDate BETWEEN :startDate AND :endDate
            ORDER BY t.settlementDate ASC, t.transactionId ASC
            """)
    List<Transaction> findBySettlementDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    /**
     * Loads only SETTLED transactions within a date window.
     * Used by reconciliation engine to identify payout-eligible transactions.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.settlementDate BETWEEN :startDate AND :endDate
            AND   t.status = :status
            ORDER BY t.transactionId ASC
            """)
    List<Transaction> findBySettlementDateBetweenAndStatus(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate,
            @Param("status")    TransactionStatus status
    );

    /**
     * Finds all transactions for a specific merchant.
     * Used for merchant-level reporting.
     */
    List<Transaction> findByMerchantId(String merchantId);
}