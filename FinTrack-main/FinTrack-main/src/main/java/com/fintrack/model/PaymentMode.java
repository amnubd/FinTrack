package com.fintrack.model;

/**
 * Enumeration representing available payment channels and settlement modes
 * for executing financial transactions.
 */
public enum PaymentMode {
    /**
     * Physical cash currency transactions.
     */
    CASH("Cash"),

    /**
     * Credit or debit payment cards.
     */
    CARD("Card"),

    /**
     * Unified Payments Interface (UPI) or instant mobile peer-to-merchant rails.
     */
    UPI("UPI"),

    /**
     * Direct bank transfers, wires, ACH, NEFT/RTGS, or net-banking settlement.
     */
    BANK_TRANSFER("Bank Transfer"),

    /**
     * Prepaid digital wallets or stored-value accounts.
     */
    WALLET("Wallet");

    private final String displayName;

    /**
     * Constructs a PaymentMode enum with its associated display label.
     *
     * @param displayName label suitable for UI display
     */
    PaymentMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-friendly display label.
     *
     * @return display label of the payment mode
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses and normalizes a string into a matching {@link PaymentMode} case-insensitively.
     *
     * @param text input string to match
     * @return the matching PaymentMode
     * @throws ValidationException if the string does not match any recognized payment mode
     */
    public static PaymentMode fromString(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Payment mode cannot be null or empty.");
        }
        String normalized = text.trim();
        for (PaymentMode mode : values()) {
            if (mode.name().equalsIgnoreCase(normalized)
                    || mode.displayName.equalsIgnoreCase(normalized)
                    || mode.name().replace("_", " ").equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        throw new ValidationException("Invalid payment mode: '" + text 
                + "'. Allowed values: CASH, CARD, UPI, BANK_TRANSFER, WALLET.");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
