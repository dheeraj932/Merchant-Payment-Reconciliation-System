package com.mprs.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard error response envelope returned by all API errors.
 *
 * Matches the format defined in the MPRS spec:
 * {
 *   "timestamp": "...",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "...",
 *   "path": "..."
 * }
 *
 * Uses Java record for immutability.
 * @JsonInclude suppresses null fields (e.g. validationErrors
 * only appears when there are field-level validation failures).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant       timestamp,
        int           status,
        String        error,
        String        message,
        String        path,
        List<String>  validationErrors    // only present on 400 validation failures
) {

    /**
     * Factory method for simple errors (no validation detail).
     */
    public static ErrorResponse of(int status, String error,
                                   String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    /**
     * Factory method for validation errors with field-level detail.
     */
    public static ErrorResponse ofValidation(String message,
                                             String path,
                                             List<String> validationErrors) {
        return new ErrorResponse(Instant.now(), 400, "Bad Request",
                message, path, validationErrors);
    }
}