package com.mprs.payout.controller;

import com.mprs.payout.domain.Payout;
import com.mprs.payout.service.PayoutService;
import com.mprs.payout.service.PayoutService.BulkIngestionResult;
import com.mprs.payout.service.PayoutService.PayoutRequest;
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
 * REST controller for payout ingestion and querying.
 *
 * POST /api/v1/payouts/bulk  — ADMIN / SYSTEM only
 * GET  /api/v1/payouts       — all authenticated roles (read-only for FINANCE_ANALYST)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
@Tag(name = "Payouts", description = "Payout ingestion and querying")
@SecurityRequirement(name = "bearerAuth")
public class PayoutController {

    private final PayoutService payoutService;

    // ── Request DTOs ─────────────────────────────────────────────

    /**
     * Single payout item in the bulk upload payload.
     */
    public record PayoutItemRequest(

            @NotNull(message = "transaction_id is required")
            String transactionId,

            @NotNull(message = "payout_amount is required")
            @Positive(message = "payout_amount must be positive")
            BigDecimal payoutAmount,

            @NotNull(message = "payout_date is required")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate payoutDate
    ) {}

    /**
     * Bulk upload request wrapper for payouts.
     */
    public record BulkPayoutRequest(
            @NotEmpty(message = "payouts list must not be empty")
            List<@Valid PayoutItemRequest> payouts
    ) {}

    // ── Endpoints ────────────────────────────────────────────────

    /**
     * POST /api/v1/payouts/bulk
     *
     * Ingests a batch of payout records.
     * Requires ADMIN or SYSTEM role (enforced by SecurityConfig).
     *
     * Returns a summary of how many were saved, skipped (duplicates), or errored.
     */
    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk upload payouts",
            description = "Ingest multiple payout records. Duplicate payouts per transaction_id are skipped."
    )
    public ResponseEntity<BulkIngestionResult> bulkUpload(
            @Valid @RequestBody BulkPayoutRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        log.info("Bulk payout upload by user: {} — {} records",
                currentUser.getUsername(), request.payouts().size());

        List<PayoutRequest> serviceRequests = request.payouts()
                .stream()
                .map(item -> new PayoutRequest(
                        item.transactionId(),
                        item.payoutAmount(),
                        item.payoutDate()
                ))
                .toList();

        BulkIngestionResult result = payoutService.ingestPayouts(serviceRequests);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/payouts
     *
     * Returns payouts, optionally filtered by payout date range.
     */
    @GetMapping
    @Operation(
            summary = "Query payouts",
            description = "Returns all payouts. Optionally filter by payout date range."
    )
    public ResponseEntity<List<Payout>> getPayouts(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Payout> results;

        if (startDate != null && endDate != null) {
            results = payoutService.getPayoutsByDateRange(startDate, endDate);
        } else {
            results = payoutService.getAllPayouts();
        }

        return ResponseEntity.ok(results);
    }
}

