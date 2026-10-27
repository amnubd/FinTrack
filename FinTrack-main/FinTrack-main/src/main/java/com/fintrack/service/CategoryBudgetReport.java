package com.fintrack.service;

import com.fintrack.model.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable report representing the financial evaluation of an individual category budget
 * for a specific billing cycle.
 *
 * @param category        the expenditure category evaluated
 * @param monthlyLimit    the ceiling allocated for the month
 * @param spentAmount     total actual expenditure incurred in the period
 * @param remainingAmount remaining unspent allocation (can be negative if exceeded)
 * @param percentageUsed  ratio of limit consumed expressed as a percentage (e.g., 82.5%)
 * @param status          evaluated {@link BudgetStatus}
 */
public record CategoryBudgetReport(
        Category category,
        BigDecimal monthlyLimit,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        double percentageUsed,
        BudgetStatus status
) {
    /**
     * Factory method creating a CategoryBudgetReport by deriving remaining balance,
     * percentage utilization, and compliance status.
     *
     * @param category     budgeted category
     * @param monthlyLimit allocated ceiling amount
     * @param spentAmount  total spent amount
     * @return constructed CategoryBudgetReport
     */
    public static CategoryBudgetReport of(Category category, BigDecimal monthlyLimit, BigDecimal spentAmount) {
        BigDecimal limit = (monthlyLimit != null) ? monthlyLimit : BigDecimal.ZERO;
        BigDecimal spent = (spentAmount != null) ? spentAmount : BigDecimal.ZERO;
        BigDecimal remaining = limit.subtract(spent);

        double percentage = 0.0;
        if (limit.compareTo(BigDecimal.ZERO) > 0) {
            percentage = spent.divide(limit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        BudgetStatus status = BudgetStatus.evaluate(spent, limit);
        return new CategoryBudgetReport(category, limit, spent, remaining, percentage, status);
    }
}
