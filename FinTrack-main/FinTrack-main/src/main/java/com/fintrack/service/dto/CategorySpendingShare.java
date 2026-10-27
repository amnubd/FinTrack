package com.fintrack.service.dto;

import com.fintrack.model.Category;

import java.math.BigDecimal;

/**
 * Encapsulates the relative volume and proportion of total expenses attributed
 * to a distinct expenditure category.
 *
 * @param category          the categorized expense group
 * @param totalSpent        total monetary amount spent in this category
 * @param percentageOfTotal percentage of total outflow across all categories (e.g., 34.2%)
 * @param transactionCount  number of distinct transactions contributing to this spend
 */
public record CategorySpendingShare(
        Category category,
        BigDecimal totalSpent,
        double percentageOfTotal,
        int transactionCount
) {}
