package com.fintrack.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Domain entity representing an authenticated user profile with global financial constraints,
 * periodic income expectations, spending limits, and milestone savings targets.
 */
public class User {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    /**
     * Unique identifier for the user profile.
     */
    private String id;

    /**
     * Full legal or display name of the user.
     */
    private String name;

    /**
     * Valid primary email address used for identity and notifications.
     */
    private String email;

    /**
     * ISO 4217 standard currency code (e.g., "USD", "INR", "EUR", "GBP").
     */
    private String currency;

    /**
     * Active display / reporting currency preference (defaults to account currency if null/blank).
     */
    private String reportingCurrency;

    /**
     * Projected or recurring regular monthly earnings. Must be non-negative (>= 0).
     */
    private BigDecimal monthlyIncome;

    /**
     * Macro-level monthly expenditure ceiling across all categories. Must be non-negative (>= 0).
     */
    private BigDecimal globalBudget;

    /**
     * Target financial savings quota or milestone allocation. Must be non-negative (>= 0).
     */
    private BigDecimal savingsGoal;

    /**
     * Constructs a fully-specified User instance with complete financial targets.
     *
     * @param id            unique identifier; if null or blank, a random UUID is generated
     * @param name          full name; must not be null or blank
     * @param email         valid email address; must not be null and must conform to email syntax
     * @param currency      standard currency code; must not be null or blank (e.g., "USD", "INR")
     * @param monthlyIncome expected regular monthly earnings; must be non-null and >= 0
     * @param globalBudget  macro expenditure ceiling; must be non-null and >= 0
     * @param savingsGoal   targeted savings reserve; must be non-null and >= 0
     * @throws ValidationException if any invariant condition is violated
     */
    public User(String id,
                String name,
                String email,
                String currency,
                BigDecimal monthlyIncome,
                BigDecimal globalBudget,
                BigDecimal savingsGoal) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        setName(name);
        setEmail(email);
        setCurrency(currency);
        setMonthlyIncome(monthlyIncome);
        setGlobalBudget(globalBudget);
        setSavingsGoal(savingsGoal);
    }

    /**
     * Convenience constructor initializing a user profile with basic identification
     * and default financial parameters (USD currency and zeroed financial targets).
     *
     * @param name  full name; must not be null or blank
     * @param email valid email address; must not be null
     * @throws ValidationException if name or email fails validation
     */
    public User(String name, String email) {
        this(UUID.randomUUID().toString(), name, email, "USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Returns the unique identifier of the user.
     *
     * @return user ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for the user.
     *
     * @param id non-null, non-blank string
     * @throws ValidationException if id is null or blank
     */
    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new ValidationException("User ID cannot be null or blank.");
        }
        this.id = id.trim();
    }

    /**
     * Returns the user's name.
     *
     * @return full name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's full name after trimming leading/trailing whitespace.
     *
     * @param name non-null, non-blank name
     * @throws ValidationException if name is null or blank
     */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("User name must be a non-empty string.");
        }
        this.name = name.trim();
    }

    /**
     * Returns the user's primary email address.
     *
     * @return email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Validates and sets the user's email address in canonical lowercase.
     *
     * @param email non-null, syntactically valid email address
     * @throws ValidationException if email is null, blank, or malformed
     */
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email cannot be null or blank.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ValidationException("Invalid email address format: '" + email + "'.");
        }
        this.email = normalizedEmail;
    }

    /**
     * Returns the active currency code.
     *
     * @return ISO currency code
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the user's currency preference in uppercase.
     *
     * @param currency non-null, non-blank currency code
     * @throws ValidationException if currency is null or blank
     */
    public void setCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new ValidationException("Currency code cannot be null or blank (e.g., 'USD', 'INR').");
        }
        this.currency = currency.trim().toUpperCase();
    }

    /**
     * Returns the active reporting/display currency preference.
     *
     * @return reporting currency code (defaults to base currency if not set)
     */
    public String getReportingCurrency() {
        return (reportingCurrency != null && !reportingCurrency.isBlank())
                ? reportingCurrency
                : getCurrency();
    }

    /**
     * Sets the active reporting/display currency preference.
     *
     * @param reportingCurrency target reporting currency code
     */
    public void setReportingCurrency(String reportingCurrency) {
        this.reportingCurrency = (reportingCurrency != null && !reportingCurrency.isBlank())
                ? reportingCurrency.trim().toUpperCase()
                : null;
    }

    /**
     * Returns the projected regular monthly income.
     *
     * @return monthly income
     */
    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    /**
     * Sets the projected regular monthly income.
     *
     * @param monthlyIncome non-null, non-negative monetary value (>= 0)
     * @throws ValidationException if monthlyIncome is null or negative
     */
    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        if (monthlyIncome == null) {
            throw new ValidationException("Monthly income cannot be null.");
        }
        if (monthlyIncome.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Monthly income cannot be negative. Received: " + monthlyIncome);
        }
        this.monthlyIncome = monthlyIncome;
    }

    /**
     * Returns the global monthly budget ceiling.
     *
     * @return global spending budget
     */
    public BigDecimal getGlobalBudget() {
        return globalBudget;
    }

    /**
     * Sets the global monthly expenditure ceiling.
     *
     * @param globalBudget non-null, non-negative monetary value (>= 0)
     * @throws ValidationException if globalBudget is null or negative
     */
    public void setGlobalBudget(BigDecimal globalBudget) {
        if (globalBudget == null) {
            throw new ValidationException("Global budget cannot be null.");
        }
        if (globalBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Global budget cannot be negative. Received: " + globalBudget);
        }
        this.globalBudget = globalBudget;
    }

    /**
     * Returns the target savings goal.
     *
     * @return savings goal threshold
     */
    public BigDecimal getSavingsGoal() {
        return savingsGoal;
    }

    /**
     * Sets the target savings goal.
     *
     * @param savingsGoal non-null, non-negative monetary value (>= 0)
     * @throws ValidationException if savingsGoal is null or negative
     */
    public void setSavingsGoal(BigDecimal savingsGoal) {
        if (savingsGoal == null) {
            throw new ValidationException("Savings goal cannot be null.");
        }
        if (savingsGoal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Savings goal cannot be negative. Received: " + savingsGoal);
        }
        this.savingsGoal = savingsGoal;
    }

    /**
     * Compares user instances for equality based on their unique identifier.
     *
     * @param o candidate object
     * @return true if both objects share the same user ID
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    /**
     * Generates a hash code for the user instance based on unique identifier.
     *
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Returns a structured string representation of the user profile and current thresholds.
     *
     * @return human-readable description of the user
     */
    @Override
    public String toString() {
        return String.format("User: %s <%s> [ID: %s] | Currency: %s | Monthly Income: %s %s | Global Budget: %s %s | Savings Goal: %s %s",
                name,
                email,
                id,
                currency,
                currency,
                monthlyIncome.toPlainString(),
                currency,
                globalBudget.toPlainString(),
                currency,
                savingsGoal.toPlainString());
    }
}
