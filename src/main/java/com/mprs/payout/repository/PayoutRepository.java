package com.mprs.payout.repository;

import com.mprs.payout.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for payouts.
 */
@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

    /**
     * Finds a payout by its business key (transaction id).
     */
    Optional<Payout> findByTransactionId(String transactionId);

    /**
     * Checks if a payout for the given transaction already exists.
     * Used for duplicate detection during bulk ingestion.
     */
    boolean existsByTransactionId(String transactionId);

    /**
     * Loads all payouts within a payout date window.
     * Note: payoutDate is stored as an ISO-8601 string (YYYY-MM-DD) in the DB,
     * so lexical ordering matches chronological ordering.
     */
    @Query("""
            SELECT p FROM Payout p
            WHERE p.payoutDate BETWEEN :startDate AND :endDate
            ORDER BY p.payoutDate ASC, p.transactionId ASC
            """)
    List<Payout> findByPayoutDateBetween(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * Finds all payouts for a specific transaction id list.
     * This is useful for reconciliation lookups.
     */
    List<Payout> findByTransactionIdIn(List<String> transactionIds);
}

