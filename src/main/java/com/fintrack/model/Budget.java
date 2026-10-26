package com.fintrack.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a category-specific spending ceiling for a designated month and year.
 */
public class Budget {

    /**
     * Unique identifier for the budget record.
     */
    private String id;

    /**
     * Associated user profile identifier.
     */
    private String userId;

    /**
     * Category governed by this budget limit.
     */
    private Category category;

    /**
     * Maximum allowed expenditure limit for the period. Must be strictly positive (> 0).
     */
    private BigDecimal monthlyLimit;

    /**
     * Budget month (1 - 12).
     */
    private int month;

    /**
     * Budget calendar year (e.g., 2026).
     */
    private int year;

    /**
     * Constructs a fully-specified Budget instance.
     *
     * @param id           unique budget identifier; auto-generated if null/blank
     * @param userId       owning user ID; must not be null or blank
     * @param category     target category; must not be null
     * @param monthlyLimit maximum expenditure limit; must be strictly greater than 0
     * @param month        calendar month (1 to 12)
     * @param year         calendar year (e.g., 2026)
     * @throws ValidationException if validation rules are violated
     */
    public Budget(String id,
                  String userId,
                  Category category,
                  BigDecimal monthlyLimit,
                  int month,
                  int year) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        setUserId(userId);
        setCategory(category);
        setMonthlyLimit(monthlyLimit);
        setMonth(month);
        setYear(year);
    }

    /**
     * Convenience constructor initializing a budget for the current month/year.
     *
     * @param userId       owning user ID; must not be null or blank
     * @param category     target category; must not be null
     * @param monthlyLimit maximum expenditure limit; must be strictly greater than 0
     */
    public Budget(String userId, Category category, BigDecimal monthlyLimit) {
        this(UUID.randomUUID().toString(),
             userId,
             category,
             monthlyLimit,
             YearMonth.now().getMonthValue(),
             YearMonth.now().getYear());
    }

    /**
     * Returns the budget ID.
     *
     * @return unique ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the budget ID.
     *
     * @param id non-null, non-blank ID
     */
    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Budget ID cannot be null or blank.");
        }
        this.id = id.trim();
    }

    /**
     * Returns the owning user ID.
     *
     * @return user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the owning user ID.
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
     * Returns the budgeted category.
     *
     * @return category
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Sets the budgeted category.
     *
     * @param category non-null category
     */
    public void setCategory(Category category) {
        if (category == null) {
            throw new ValidationException("Budget category cannot be null.");
        }
        this.category = category;
    }

    /**
     * Returns the monthly monetary limit.
     *
     * @return limit amount
     */
    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    /**
     * Sets the monthly expenditure limit.
     *
     * @param monthlyLimit monetary limit; must be strictly greater than 0
     */
    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        if (monthlyLimit == null) {
            throw new ValidationException("Monthly limit cannot be null.");
        }
        if (monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Monthly limit must be strictly positive (> 0). Received: " + monthlyLimit);
        }
        this.monthlyLimit = monthlyLimit;
    }

    /**
     * Returns the budget month (1 - 12).
     *
     * @return month number
     */
    public int getMonth() {
        return month;
    }

    /**
     * Sets the budget month.
     *
     * @param month month integer between 1 and 12
     */
    public void setMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ValidationException("Month must be between 1 and 12. Received: " + month);
        }
        this.month = month;
    }

    /**
     * Returns the calendar year.
     *
     * @return year integer
     */
    public int getYear() {
        return year;
    }

    /**
     * Sets the calendar year.
     *
     * @param year calendar year (>= 2000)
     */
    public void setYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new ValidationException("Year must be between 2000 and 2100. Received: " + year);
        }
        this.year = year;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Budget budget = (Budget) o;
        return Objects.equals(id, budget.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Budget [%s-%02d] | %s: %s (User: %s)",
                year, month, category.getDisplayName(), monthlyLimit.toPlainString(), userId);
    }
}
