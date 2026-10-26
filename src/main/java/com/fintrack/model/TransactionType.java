package com.fintrack.model;

/**
 * Defines the direction of capital flow in a financial transaction.
 */
public enum TransactionType {
    /**
     * Financial inflow increasing the user's available balance (e.g., salary, investment dividend).
     */
    INCOME("Income"),

    /**
     * Financial outflow reducing the user's available balance (e.g., groceries, utility bills).
     */
    EXPENSE("Expense");

    private final String displayName;

    /**
     * Constructs a TransactionType enum with a human-readable display name.
     *
     * @param displayName label suitable for UI display
     */
    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-friendly display label.
     *
     * @return display label of the transaction type
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolves a case-insensitive string into a corresponding {@link TransactionType}.
     *
     * @param text input string representing the transaction type
     * @return the matching TransactionType
     * @throws ValidationException if the text does not correspond to a valid TransactionType
     */
    public static TransactionType fromString(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Transaction type cannot be null or empty.");
        }
        for (TransactionType type : values()) {
            if (type.name().equalsIgnoreCase(text.trim()) || type.displayName.equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new ValidationException("Invalid transaction type: '" + text + "'. Expected INCOME or EXPENSE.");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
