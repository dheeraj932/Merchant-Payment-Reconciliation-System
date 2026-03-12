package com.mprs.shared.exception;

import com.mprs.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralized exception handling for all controllers.
 *
 * Every exception thrown anywhere in the application
 * is caught here and converted to a consistent ErrorResponse.
 *
 * This means controllers never need try/catch blocks —
 * they just throw and this handler formats the response.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bad Request ──────────────────────────────────────────

    /**
     * Handles @Valid / @Validated bean validation failures.
     * Collects all field errors into a list for the response.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .sorted()
                .toList();

        log.warn("Validation failed on {}: {}", request.getRequestURI(), errors);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.ofValidation(
                        "Validation failed",
                        request.getRequestURI(),
                        errors
                ));
    }

    /**
     * Handles business rule validation failures.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {

        log.warn("Business validation error on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage(),
                        request.getRequestURI()));
    }

    // ── 401 Unauthorized ─────────────────────────────────────────

    /**
     * Handles invalid username or password during login.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        log.warn("Bad credentials attempt on {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized",
                        "Invalid username or password",
                        request.getRequestURI()));
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ResponseEntity<ErrorResponse> handleAccountStatus(
            RuntimeException ex,
            HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Unauthorized",
                        ex.getMessage(), request.getRequestURI()));
    }

    // ── 403 Forbidden ────────────────────────────────────────────

    /**
     * Handles role-based access denial.
     * e.g. FINANCE_ANALYST trying to trigger a reconciliation run.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied on {} - {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden",
                        "You do not have permission to access this resource",
                        request.getRequestURI()));
    }

    // ── 404 Not Found ────────────────────────────────────────────

    /**
     * Handles missing resources.
     * e.g. job ID that doesn't exist.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found",
                        ex.getMessage(), request.getRequestURI()));
    }

    // ── 409 Conflict ─────────────────────────────────────────────

    /**
     * Handles duplicate resource creation attempts.
     * e.g. duplicate payout or duplicate reconciliation job.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex,
            HttpServletRequest request) {

        log.warn("Duplicate resource on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict",
                        ex.getMessage(), request.getRequestURI()));
    }

    // ── 500 Internal Server Error ────────────────────────────────

    /**
     * Catch-all handler — logs the full stack trace and returns
     * a safe generic message (never expose internal details to clients).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaught(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later.",
                        request.getRequestURI()));
    }
}