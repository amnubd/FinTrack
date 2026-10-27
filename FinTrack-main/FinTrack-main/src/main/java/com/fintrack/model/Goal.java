package com.fintrack.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a financial milestone savings target.
 */
public class Goal {

    /**
     * Unique identifier for the goal record.
     */
    private String id;

    /**
     * Associated user profile identifier.
     */
    private String userId;

    /**
     * Name or title of the financial goal (e.g., "Emergency Fund", "Down Payment").
     */
    private String name;

    /**
     * Target monetary value to accumulate. Must be strictly positive (> 0).
     */
    private BigDecimal targetAmount;

    /**
     * ISO 4217 standard currency code.
     */
    private String currency;

    /**
     * Current accumulated savings toward this goal. Must be non-negative (>= 0).
     */
    private BigDecimal currentAmount;

    /**
     * Target deadline date for achieving the milestone.
     */
    private LocalDate targetDate;

    /**
     * Constructs a fully-specified Goal instance.
     *
     * @param id            unique identifier; auto-generated if null/blank
     * @param userId        owning user identifier; must not be null or blank
     * @param name          goal title; must not be null or blank
     * @param targetAmount  target monetary accumulation; must be strictly positive (> 0)
     * @param currentAmount current progress amount; must be non-negative (>= 0)
     * @param targetDate    deadline date; must not be null
     * @throws ValidationException if any invariant condition is violated
     */
    public Goal(String id,
                String userId,
                String name,
                BigDecimal targetAmount,
                BigDecimal currentAmount,
                String currency,
                LocalDate targetDate) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        setUserId(userId);
        setName(name);
        setTargetAmount(targetAmount);
        setCurrentAmount(currentAmount);
        setCurrency(currency);
        setTargetDate(targetDate);
    }

    public Goal(String id,
                String userId,
                String name,
                BigDecimal targetAmount,
                BigDecimal currentAmount,
                LocalDate targetDate) {
        this(id, userId, name, targetAmount, currentAmount, "USD", targetDate);
    }

    /**
     * Convenience constructor initializing a new goal with zero current progress.
     *
     * @param userId       owning user ID
     * @param name         goal title
     * @param targetAmount target monetary threshold
     * @param targetDate   target deadline date
     */
    public Goal(String userId, String name, BigDecimal targetAmount, LocalDate targetDate) {
        this(UUID.randomUUID().toString(), userId, name, targetAmount, BigDecimal.ZERO, targetDate);
    }

    /**
     * Returns the goal ID.
     *
     * @return unique ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the goal ID.
     *
     * @param id non-null, non-blank ID
     */
    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Goal ID cannot be null or blank.");
        }
        this.id = id.trim();
    }

    /**
     * Returns the user ID.
     *
     * @return user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID.
     *
     * @param userId non-null, non-blank user ID
     */
    public void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ValidationException("User ID cannot be null or blank.");
        }
        this.userId = userId.trim();
    }

    /**
     * Returns the goal title.
     *
     * @return goal title
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the goal title.
     *
     * @param name non-null, non-blank string
     */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Goal name cannot be null or blank.");
        }
        this.name = name.trim();
    }

    /**
     * Returns the target amount.
     *
     * @return target amount
     */
    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    /**
     * Sets the target amount.
     *
     * @param targetAmount strictly positive monetary amount
     */
    public void setTargetAmount(BigDecimal targetAmount) {
        if (targetAmount == null) {
            throw new ValidationException("Target amount cannot be null.");
        }
        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Target amount must be strictly positive (> 0). Received: " + targetAmount);
        }
        this.targetAmount = targetAmount;
    }

    /**
     * Returns the ISO currency code for this milestone goal.
     *
     * @return ISO currency code
     */
    public String getCurrency() {
        return (currency != null && !currency.isBlank()) ? currency : "USD";
    }

    /**
     * Sets the ISO currency code for this milestone goal.
     *
     * @param currency ISO currency code
     */
    public void setCurrency(String currency) {
        this.currency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "USD";
    }

    /**
     * Returns the current accumulated amount.
     *
     * @return current amount
     */
    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    /**
     * Sets the current accumulated amount.
     *
     * @param currentAmount non-negative monetary amount (>= 0)
     */
    public void setCurrentAmount(BigDecimal currentAmount) {
        if (currentAmount == null) {
            throw new ValidationException("Current amount cannot be null.");
        }
        if (currentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Current amount cannot be negative. Received: " + currentAmount);
        }
        this.currentAmount = currentAmount;
    }

    /**
     * Returns the target milestone date.
     *
     * @return target date
     */
    public LocalDate getTargetDate() {
        return targetDate;
    }

    /**
     * Sets the target milestone date.
     *
     * @param targetDate non-null date
     */
    public void setTargetDate(LocalDate targetDate) {
        if (targetDate == null) {
            throw new ValidationException("Target date cannot be null.");
        }
        this.targetDate = targetDate;
    }

    /**
     * Calculates the current progress ratio percentage (0.0 to 100.0+).
     *
     * @return percentage progress completed
     */
    public double getProgressPercentage() {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return currentAmount.divide(targetAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Goal goal = (Goal) o;
        return Objects.equals(id, goal.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Goal: '%s' [%.1f%%] | %s / %s (Target Date: %s)",
                name, getProgressPercentage(), currentAmount.toPlainString(), targetAmount.toPlainString(), targetDate);
    }
}
