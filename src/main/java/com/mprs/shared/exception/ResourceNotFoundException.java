package com.mprs.shared.exception;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404 Not Found.
 *
 * Examples:
 * - Reconciliation job ID not found
 * - Transaction ID not found
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}