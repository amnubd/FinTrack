package com.fintrack.service.dto;

import com.fintrack.model.Category;

import java.math.BigDecimal;

/**
 * Encapsulates the burn rate and Month-over-Month (MoM) spending trajectory
 * for a specific financial category.
 *
 * @param category              the expense category evaluated
 * @param currentSpent          actual spend accumulated so far in the current month
 * @param dailyRunRate          average spend per elapsed day in the active month
 * @param projectedMonthlySpend linear projection of month-end expenditure
 * @param previousMonthSpent    actual total spend incurred in the preceding calendar month
 * @param absoluteChange        monetary difference (projected spend minus previous month spend)
 * @param percentageChange      percentage variance relative to preceding month (e.g., +15.5%)
 */
public record CategoryRunRate(
        Category category,
        BigDecimal currentSpent,
        BigDecimal dailyRunRate,
        BigDecimal projectedMonthlySpend,
        BigDecimal previousMonthSpent,
        BigDecimal absoluteChange,
        double percentageChange
) {}
