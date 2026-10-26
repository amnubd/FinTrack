package com.fintrack.model;

/**
 * Custom runtime exception thrown when domain model validation rules
 * or business invariant constraints are violated.
 */
public class ValidationException extends RuntimeException {

    /**
     * Constructs a new ValidationException with the specified detail message.
     *
     * @param message human-readable description of the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new ValidationException with the specified detail message and root cause.
     *
     * @param message human-readable description of the validation failure
     * @param cause   underlying cause of the exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
