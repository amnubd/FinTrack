package com.fintrack.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing an individual financial transaction (inflow or outflow).
 * Enforces business invariants including non-null constraints, strict positivity
 * on monetary amounts, and defensive copy/sanitization of notes.
 */
public class Transaction {

    private static final DateTimeFormatter TO_STRING_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Unique identifier for the transaction record.
     */
    private String id;

    /**
     * Associated user profile identifier.
     */
    private String userId;

    /**
     * Exact date and time when the transaction took place.
     */
    private LocalDateTime timestamp;

    /**
     * Domain category classification for budgeting and reporting.
     */
    private Category category;

    /**
     * Exact monetary amount of the transaction. Must be strictly positive (> 0).
     */
    private BigDecimal amount;

    /**
     * Settlement mechanism or channel utilized.
     */
    private PaymentMode paymentMode;

    /**
     * Directional capital flow type (INCOME or EXPENSE).
     */
    private TransactionType type;

    /**
     * Optional contextual note or memo regarding the transaction.
     */
    private String note;

    /**
     * Fully-specified constructor creating a Transaction with associated user identifier.
     *
     * @param id          unique identifier; if null or blank, a random UUID is generated
     * @param userId      associated user profile identifier; must not be null or blank
     * @param timestamp   transaction occurrence date and time; must not be null
     * @param category    transaction category; must not be null
     * @param amount      monetary value; must not be null and must be strictly greater than 0
     * @param paymentMode payment channel; must not be null
     * @param type        flow direction; must not be null
     * @param note        optional description; null values are sanitized to empty strings
     * @throws ValidationException if any invariant condition is violated
     */
    public Transaction(String id,
                       String userId,
                       LocalDateTime timestamp,
                       Category category,
                       BigDecimal amount,
                       PaymentMode paymentMode,
                       TransactionType type,
                       String note) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        setUserId(userId);
        setTimestamp(timestamp);
        setCategory(category);
        setAmount(amount);
        setPaymentMode(paymentMode);
        setType(type);
        setNote(note);
    }

    /**
     * Primary constructor creating a Transaction instance with default user reference.
     *
     * @param id          unique identifier; if null or blank, a random UUID is generated
     * @param timestamp   transaction occurrence date and time; must not be null
     * @param category    transaction category; must not be null
     * @param amount      monetary value; must not be null and must be strictly greater than 0
     * @param paymentMode payment channel; must not be null
     * @param type        flow direction; must not be null
     * @param note        optional description; null values are sanitized to empty strings
     * @throws ValidationException if any invariant condition is violated
     */
    public Transaction(String id,
                       LocalDateTime timestamp,
                       Category category,
                       BigDecimal amount,
                       PaymentMode paymentMode,
                       TransactionType type,
                       String note) {
        this(id, "default_user", timestamp, category, amount, paymentMode, type, note);
    }

    /**
     * Convenience constructor that auto-generates a unique identifier
     * and timestamps the transaction with the current system time.
     *
     * @param category    transaction category; must not be null
     * @param amount      monetary value; must not be null and must be strictly greater than 0
     * @param paymentMode payment channel; must not be null
     * @param type        flow direction; must not be null
     * @param note        optional description; null values are sanitized to empty strings
     * @throws ValidationException if any invariant condition is violated
     */
    public Transaction(Category category,
                       BigDecimal amount,
                       PaymentMode paymentMode,
                       TransactionType type,
                       String note) {
        this(UUID.randomUUID().toString(), LocalDateTime.now(), category, amount, paymentMode, type, note);
    }

    /**
     * Convenience constructor with custom timestamp and auto-generated identifier.
     *
     * @param timestamp   transaction date and time; must not be null
     * @param category    transaction category; must not be null
     * @param amount      monetary value; must not be null and must be strictly greater than 0
     * @param paymentMode payment channel; must not be null
     * @param type        flow direction; must not be null
     * @param note        optional description; null values are sanitized to empty strings
     * @throws ValidationException if any invariant condition is violated
     */
    public Transaction(LocalDateTime timestamp,
                       Category category,
                       BigDecimal amount,
                       PaymentMode paymentMode,
                       TransactionType type,
                       String note) {
        this(UUID.randomUUID().toString(), timestamp, category, amount, paymentMode, type, note);
    }

    /**
     * Returns the unique identifier.
     *
     * @return transaction ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier.
     *
     * @param id non-null, non-blank transaction ID
     * @throws ValidationException if id is null or blank
     */
    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Transaction ID cannot be null or blank.");
        }
        this.id = id.trim();
    }

    /**
     * Returns the associated user identifier.
     *
     * @return user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the associated user identifier.
     *
     * @param userId non-null, non-blank user ID
     * @throws ValidationException if userId is null or blank
     */
    public void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ValidationException("User ID cannot be null or blank.");
        }
        this.userId = userId.trim();
    }

    /**
     * Returns the timestamp when the transaction occurred.
     *
     * @return transaction timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of the transaction.
     *
     * @param timestamp non-null date and time
     * @throws ValidationException if timestamp is null
     */
    public void setTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new ValidationException("Transaction timestamp cannot be null.");
        }
        this.timestamp = timestamp;
    }

    /**
     * Returns the category classification.
     *
     * @return transaction category
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Sets the category classification.
     *
     * @param category non-null Category
     * @throws ValidationException if category is null
     */
    public void setCategory(Category category) {
        if (category == null) {
            throw new ValidationException("Transaction category cannot be null.");
        }
        this.category = category;
    }

    /**
     * Returns the monetary amount.
     *
     * @return transaction amount as a {@link BigDecimal}
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the monetary amount.
     *
     * @param amount non-null, strictly positive monetary value
     * @throws ValidationException if amount is null or less than or equal to zero
     */
    public void setAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("Transaction amount cannot be null.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Transaction amount must be strictly greater than 0. Received: " + amount);
        }
        this.amount = amount;
    }

    /**
     * Returns the payment mode.
     *
     * @return payment channel used
     */
    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    /**
     * Sets the payment mode.
     *
     * @param paymentMode non-null PaymentMode
     * @throws ValidationException if paymentMode is null
     */
    public void setPaymentMode(PaymentMode paymentMode) {
        if (paymentMode == null) {
            throw new ValidationException("Transaction payment mode cannot be null.");
        }
        this.paymentMode = paymentMode;
    }

    /**
     * Returns the flow type (INCOME or EXPENSE).
     *
     * @return transaction type
     */
    public TransactionType getType() {
        return type;
    }

    /**
     * Sets the flow type.
     *
     * @param type non-null TransactionType
     * @throws ValidationException if type is null
     */
    public void setType(TransactionType type) {
        if (type == null) {
            throw new ValidationException("Transaction type cannot be null.");
        }
        this.type = type;
    }

    /**
     * Returns the contextual note.
     *
     * @return transaction note (guaranteed non-null, may be empty)
     */
    public String getNote() {
        return note;
    }

    /**
     * Sets the contextual note.
     *
     * @param note optional string; null values are converted to empty strings
     */
    public void setNote(String note) {
        this.note = (note == null) ? "" : note.trim();
    }

    /**
     * Compares transactions for equality based on their unique identifier.
     *
     * @param o candidate object
     * @return true if both objects represent the same transaction identifier
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(id, that.id);
    }

    /**
     * Generates hash code based on the transaction identifier.
     *
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Returns a structured, human-readable string representation of the transaction.
     *
     * @return formatted string containing timestamp, type, mode, category, amount, and note
     */
    @Override
    public String toString() {
        String sign = (type == TransactionType.INCOME) ? "+" : "-";
        String noteSuffix = note.isEmpty() ? "" : " | Note: " + note;
        return String.format("[%s] %s (%s) | %s: %s%s%s",
                timestamp.format(TO_STRING_FORMATTER),
                type.name(),
                paymentMode.getDisplayName(),
                category.getDisplayName(),
                sign,
                amount.toPlainString(),
                noteSuffix);
    }
}
