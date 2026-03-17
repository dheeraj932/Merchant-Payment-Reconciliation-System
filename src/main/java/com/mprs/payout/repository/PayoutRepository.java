package com.mprs.payout.repository;

import com.mprs.payout.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for payouts.
 * Spring Data JPA generates all implementations at runtime.
 */
@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

    /**
     * Finds a payout by transaction ID.
     * Used for duplicate detection during bulk ingestion
     * and for matching during reconciliation.
     */
    Optional<Payout> findByTransactionId(String transactionId);

    /**
     * Checks if a payout for the given transaction already exists.
     * More efficient than findByTransactionId when only existence matters.
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Loads all payouts within a payout date window.
     * Core query for the reconciliation engine.
     */
    @Query("""
            SELECT p FROM Payout p
            WHERE p.payoutDate BETWEEN :startDate AND :endDate
            ORDER BY p.payoutDate ASC, p.transactionId ASC
            """)
    List<Payout> findByPayoutDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    /**
     * Finds all payouts for a list of transaction IDs.
     * Used by the reconciliation engine to bulk-load
     * payouts matching a set of transactions efficiently.
     */
    @Query("""
            SELECT p FROM Payout p
            WHERE p.transactionId IN :transactionIds
            """)
    List<Payout> findByTransactionIdIn(
            @Param("transactionIds") List<String> transactionIds
    );
}