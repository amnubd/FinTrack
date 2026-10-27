package com.fintrack.service.dto;

import java.math.BigDecimal;

/**
 * Encapsulates transaction velocity metrics over a specified historical window,
 * capturing the frequency and volume of expenditures per day and per week.
 *
 * @param dailyTransactionCount  average transaction frequency per day
 * @param dailySpend             average monetary spend per day
 * @param weeklyTransactionCount average transaction frequency per week
 * @param weeklySpend            average monetary spend per week
 * @param totalTransactions      total count of transactions evaluated
 * @param totalSpend             total monetary outflow evaluated
 * @param daysAnalyzed           number of calendar days in the evaluation window
 */
public record VelocityMetrics(
        double dailyTransactionCount,
        BigDecimal dailySpend,
        double weeklyTransactionCount,
        BigDecimal weeklySpend,
        int totalTransactions,
        BigDecimal totalSpend,
        int daysAnalyzed
) {
    public static VelocityMetrics empty() {
        return new VelocityMetrics(0.0, BigDecimal.ZERO, 0.0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0);
    }
}
