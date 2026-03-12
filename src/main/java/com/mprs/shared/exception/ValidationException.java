package com.mprs.shared.exception;

/**
 * Thrown when business rule validation fails.
 * Maps to HTTP 400 Bad Request.
 *
 * Distinct from @Valid bean validation failures —
 * this is for domain/business logic validation.
 *
 * Examples:
 * - Transaction amount is negative
 * - Reconciliation end date is before start date
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}