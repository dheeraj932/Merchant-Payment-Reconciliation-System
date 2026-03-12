package com.mprs.transaction.controller;

import com.mprs.transaction.domain.Transaction;
import com.mprs.transaction.service.TransactionService;
import com.mprs.transaction.service.TransactionService.BulkIngestionResult;
import com.mprs.transaction.service.TransactionService.TransactionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for transaction ingestion and querying.
 *
 * POST /api/v1/transactions/bulk  — ADMIN / SYSTEM only
 * GET  /api/v1/transactions       — all authenticated roles
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction ingestion and querying")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    // ── Request DTO ───────────────────────────────────────────────

    /**
     * Single transaction item in the bulk upload payload.
     */
    public record TransactionItemRequest(

            @NotNull(message = "transaction_id is required")
            String transactionId,

            @NotNull(message = "amount is required")
            @Positive(message = "amount must be positive")
            BigDecimal amount,

            @NotNull(message = "status is required")
            com.mprs.transaction.domain.TransactionStatus status,

            @NotNull(message = "settlement_date is required")
            LocalDate settlementDate,

            @NotNull(message = "merchant_id is required")
            String merchantId
    ) {}

    /**
     * Bulk upload request wrapper.
     */
    public record BulkTransactionRequest(
            @NotEmpty(message = "transactions list must not be empty")
            List<@Valid TransactionItemRequest> transactions
    ) {}

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * POST /api/v1/transactions/bulk
     *
     * Ingests a batch of transaction records.
     * Requires ADMIN or SYSTEM role (enforced by SecurityConfig).
     *
     * Returns a summary of how many were saved, skipped, or errored.
     */
    @PostMapping("/bulk")
    @Operation(
            summary     = "Bulk upload transactions",
            description = "Ingest multiple transaction records. Duplicates are skipped."
    )
    public ResponseEntity<BulkIngestionResult> bulkUpload(
            @Valid @RequestBody BulkTransactionRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        log.info("Bulk transaction upload by user: {} — {} records",
                currentUser.getUsername(), request.transactions().size());

        // Map controller DTOs → service DTOs
        List<TransactionRequest> serviceRequests = request.transactions()
                .stream()
                .map(item -> new TransactionRequest(
                        item.transactionId(),
                        item.amount(),
                        item.status(),
                        item.settlementDate(),
                        item.merchantId()
                ))
                .toList();

        BulkIngestionResult result = transactionService.ingestTransactions(serviceRequests);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/transactions
     *
     * Returns transactions, optionally filtered by date range.
     * All authenticated roles can access this endpoint.
     */
    @GetMapping
    @Operation(
            summary     = "Query transactions",
            description = "Returns all transactions. Optionally filter by settlement date range."
    )
    public ResponseEntity<List<Transaction>> getTransactions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<Transaction> results;

        if (startDate != null && endDate != null) {
            results = transactionService.getTransactionsByDateRange(startDate, endDate);
        } else {
            results = transactionService.getAllTransactions();
        }

        return ResponseEntity.ok(results);
    }
}