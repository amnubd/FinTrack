package com.fintrack.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Enumeration representing the real-time compliance status of a category budget.
 * <ul>
 *     <li>{@link #OK}: Spend is strictly below 75% of the allocated monthly limit.</li>
 *     <li>{@link #WARNING}: Spend is between 75% and 100% (inclusive) of the allocated limit.</li>
 *     <li>{@link #EXCEEDED}: Spend has breached 100% of the allocated monthly ceiling.</li>
 * </ul>
 */
public enum BudgetStatus {

    /**
     * Spending is well within safe thresholds (&lt; 75% of budget limit).
     */
    OK("Safe (< 75%)"),

    /**
     * Spending has entered the caution threshold (75% to 100% of budget limit).
     */
    WARNING("Warning (75% - 100%)"),

    /**
     * Spending has exceeded 100% of the allocated budget limit.
     */
    EXCEEDED("Exceeded (> 100%)");

    private static final BigDecimal SEVENTY_FIVE_PERCENT = new BigDecimal("0.75");

    private final String description;

    BudgetStatus(String description) {
        this.description = description;
    }

    /**
     * Returns the human-readable description of the compliance status.
     *
     * @return status description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Resolves the {@link BudgetStatus} given the spent amount and the allocated budget limit.
     *
     * @param spent spent amount; must not be null
     * @param limit allocated budget limit; must not be null
     * @return the evaluated {@link BudgetStatus}
     */
    public static BudgetStatus evaluate(BigDecimal spent, BigDecimal limit) {
        if (spent == null || limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            return (spent != null && spent.compareTo(BigDecimal.ZERO) > 0) ? EXCEEDED : OK;
        }

        BigDecimal warningThreshold = limit.multiply(SEVENTY_FIVE_PERCENT);

        if (spent.compareTo(warningThreshold) < 0) {
            return OK;
        } else if (spent.compareTo(limit) <= 0) {
            return WARNING;
        } else {
            return EXCEEDED;
        }
    }

    @Override
    public String toString() {
        return description;
    }
}
