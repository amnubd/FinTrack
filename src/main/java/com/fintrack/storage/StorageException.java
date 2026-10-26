package com.fintrack.storage;

/**
 * Unchecked exception thrown when an infrastructure, I/O, database,
 * or serialization error occurs within the storage layer.
 */
public class StorageException extends RuntimeException {

    /**
     * Constructs a new StorageException with the specified error message.
     *
     * @param message descriptive error message
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Constructs a new StorageException with the specified error message and cause.
     *
     * @param message descriptive error message
     * @param cause   the root cause exception (e.g., SQLException, IOException)
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
