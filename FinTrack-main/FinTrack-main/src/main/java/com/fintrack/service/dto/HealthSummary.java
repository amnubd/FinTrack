package com.fintrack.service.dto;

import java.math.BigDecimal;

/**
 * High-level diagnostic score card encapsulating macro financial indicators,
 * including savings rate, budget compliance index, health tier, and recommendations.
 *
 * @param totalIncome           total inflow for the evaluation period
 * @param totalExpenses         total outflow for the evaluation period
 * @param netSavings            retained capital (inflow minus outflow)
 * @param savingsRate           percentage of earnings retained (e.g., 28.5%)
 * @param budgetComplianceScore composite metric (0 to 100) scoring spending adherence
 * @param healthRating          qualitative tier: "EXCELLENT", "HEALTHY", "MODERATE", "CRITICAL"
 * @param actionableInsight     contextual coaching note or warning
 */
public record HealthSummary(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal netSavings,
        double savingsRate,
        int budgetComplianceScore,
        String healthRating,
        String actionableInsight
) {}
