package com.fintrack.model;

/**
 * Categorical classifications for income inflows and expense outflows.
 * Each category provides a display name and standard associated {@link TransactionType}.
 */
public enum Category {

    // --- Income Categories ---
    /**
     * Regular earned employment compensation.
     */
    SALARY("Salary", TransactionType.INCOME),

    /**
     * Inflow from freelance, contracting, or secondary side projects.
     */
    FREELANCE("Freelance", TransactionType.INCOME),

    /**
     * Returns on capital, stocks, dividends, interest, or real estate gains.
     */
    INVESTMENTS("Investments", TransactionType.INCOME),

    /**
     * Monetary gifts, grants, bonuses, or awards received.
     */
    GIFTS("Gifts", TransactionType.INCOME),

    /**
     * Miscellaneous or unclassified financial income.
     */
    OTHER_INCOME("Other Income", TransactionType.INCOME),

    // --- Expense Categories ---
    /**
     * Dining out, cafes, restaurants, and food delivery.
     */
    FOOD_AND_DINING("Food & Dining", TransactionType.EXPENSE),

    /**
     * Supermarket, pantry, and daily household provisions.
     */
    GROCERIES("Groceries", TransactionType.EXPENSE),

    /**
     * Rent, mortgage payments, maintenance, and property dues.
     */
    HOUSING("Housing", TransactionType.EXPENSE),

    /**
     * Power, water, gas, internet, mobile recharge, and recurring bills.
     */
    UTILITIES("Utilities", TransactionType.EXPENSE),

    /**
     * Public transit, fuel, vehicle maintenance, tolls, and cab fares.
     */
    TRANSPORTATION("Transportation", TransactionType.EXPENSE),

    /**
     * Streaming services, movies, events, concerts, and recreational hobbies.
     */
    ENTERTAINMENT("Entertainment", TransactionType.EXPENSE),

    /**
     * Medical appointments, pharmacy, insurance premiums, and wellness.
     */
    HEALTHCARE("Healthcare", TransactionType.EXPENSE),

    /**
     * Tuition fees, books, courses, training, and certifications.
     */
    EDUCATION("Education", TransactionType.EXPENSE),

    /**
     * Apparel, electronics, gadgets, and personal merchandise.
     */
    SHOPPING("Shopping", TransactionType.EXPENSE),

    /**
     * Flights, accommodation, vacation expenses, and tourist transit.
     */
    TRAVEL("Travel", TransactionType.EXPENSE),

    /**
     * Allocations directed to emergency funds, milestone accounts, or fixed deposits.
     */
    SAVINGS("Savings", TransactionType.EXPENSE),

    /**
     * Loan installments, credit card dues, EMI, and interest payments.
     */
    DEBT_REPAYMENT("Debt Repayment", TransactionType.EXPENSE),

    /**
     * General or uncategorized expenses.
     */
    MISCELLANEOUS("Miscellaneous", TransactionType.EXPENSE);

    private final String displayName;
    private final TransactionType defaultType;

    /**
     * Constructs a Category enum.
     *
     * @param displayName human-readable label
     * @param defaultType typical TransactionType associated with this category
     */
    Category(String displayName, TransactionType defaultType) {
        this.displayName = displayName;
        this.defaultType = defaultType;
    }

    /**
     * Returns the human-readable display label.
     *
     * @return category display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the default {@link TransactionType} associated with this category.
     *
     * @return the default transaction flow type
     */
    public TransactionType getDefaultType() {
        return defaultType;
    }

    /**
     * Resolves a category from an input string matching either the enum constant name
     * or the user-facing display label.
     *
     * @param text input string to parse
     * @return the resolved {@link Category}
     * @throws ValidationException if the text cannot be resolved to any recognized category
     */
    public static Category fromString(String text) {
        if (text == null || text.isBlank()) {
            throw new ValidationException("Category cannot be null or empty.");
        }
        String normalized = text.trim();
        for (Category category : values()) {
            if (category.name().equalsIgnoreCase(normalized)
                    || category.displayName.equalsIgnoreCase(normalized)
                    || category.name().replace("_", " ").equalsIgnoreCase(normalized)) {
                return category;
            }
        }
        throw new ValidationException("Invalid category: '" + text + "'.");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
