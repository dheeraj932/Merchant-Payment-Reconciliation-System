package com.mprs.shared.exception;

/**
 * Thrown when a resource already exists and cannot be duplicated.
 * Maps to HTTP 409 Conflict.
 *
 * Examples:
 * - Duplicate payout for the same transaction_id
 * - Duplicate reconciliation job for the same date window
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}