package com.mprs.reconciliation.controller;

import com.mprs.reconciliation.domain.ExceptionType;
import com.mprs.reconciliation.domain.ReconciliationJob;
import com.mprs.reconciliation.service.ReconciliationService;
import com.mprs.reconciliation.service.ReconciliationService.ExceptionDetail;
import com.mprs.reconciliation.service.ReconciliationService.ReconciliationSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for reconciliation job management and reporting.
 *
 * POST /api/v1/reconciliation/run              — ADMIN/SYSTEM only
 * GET  /api/v1/reconciliation/{jobId}          — all roles
 * GET  /api/v1/reconciliation/{jobId}/summary  — all roles
 * GET  /api/v1/reconciliation/{jobId}/exceptions — all roles
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "Trigger and monitor reconciliation jobs")
@SecurityRequirement(name = "bearerAuth")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    // ── Request DTO ───────────────────────────────────────────────

    /**
     * Request body for triggering a reconciliation run.
     */
    public record ReconciliationRequest(
            @NotNull(message = "start_date is required")
            LocalDate startDate,

            @NotNull(message = "end_date is required")
            LocalDate endDate
    ) {}

    // ── Endpoints ─────────────────────────────────────────────────

    /**
     * POST /api/v1/reconciliation/run
     *
     * Triggers a new reconciliation job for the given date window.
     * Returns 202 Accepted immediately — processing happens async.
     * Use the returned jobId to poll for status and results.
     */
    @PostMapping("/run")
    @Operation(
            summary     = "Trigger reconciliation job",
            description = "Starts an async reconciliation job. Returns jobId to poll for results."
    )
    public ResponseEntity<ReconciliationJob> triggerReconciliation(
            @Valid @RequestBody ReconciliationRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        log.info("Reconciliation run requested by {} for {} to {}",
                currentUser.getUsername(), request.startDate(), request.endDate());

        ReconciliationJob job = reconciliationService.triggerReconciliation(
                request.startDate(),
                request.endDate(),
                currentUser.getUsername()
        );

        // 202 Accepted — job created, processing in background
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * GET /api/v1/reconciliation/{jobId}
     *
     * Returns the current status of a reconciliation job.
     * Poll this endpoint to check if job is PENDING/RUNNING/COMPLETED/FAILED.
     */
    @GetMapping("/{jobId}")
    @Operation(
            summary     = "Get job status",
            description = "Returns current status of the reconciliation job."
    )
    public ResponseEntity<ReconciliationJob> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(reconciliationService.getJob(jobId));
    }

    /**
     * GET /api/v1/reconciliation/{jobId}/summary
     *
     * Returns the summary report for a completed reconciliation job.
     * Includes totals, variance, and exception counts by type.
     */
    @GetMapping("/{jobId}/summary")
    @Operation(
            summary     = "Get reconciliation summary",
            description = "Returns aggregated summary: totals, variance, exception counts."
    )
    public ResponseEntity<ReconciliationSummary> getSummary(@PathVariable String jobId) {
        return ResponseEntity.ok(reconciliationService.getSummary(jobId));
    }

    /**
     * GET /api/v1/reconciliation/{jobId}/exceptions
     *
     * Returns the detailed exception list for a reconciliation job.
     * Optionally filter by exception type via query param.
     *
     * Example: /exceptions?type=MISSING_PAYOUT
     */
    @GetMapping("/{jobId}/exceptions")
    @Operation(
            summary     = "Get exception details",
            description = "Returns per-transaction exception details. Filter by type optional."
    )
    public ResponseEntity<List<ExceptionDetail>> getExceptions(
            @PathVariable String jobId,
            @RequestParam(required = false) ExceptionType type) {

        return ResponseEntity.ok(reconciliationService.getExceptions(jobId, type));
    }
}