package com.fintrack.service.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Projects income, expense, net accumulation, and cumulative liquidity for a future calendar month.
 *
 * @param period                   the future month/year being forecasted
 * @param projectedIncome          estimated total inflows for the month
 * @param projectedExpense         estimated total outflows based on run-rates
 * @param projectedNetSavings      projected net savings (inflow minus outflow)
 * @param projectedEndingBalance   cumulative projected cash balance at the end of the month
 */
public record MonthlyForecast(
        YearMonth period,
        BigDecimal projectedIncome,
        BigDecimal projectedExpense,
        BigDecimal projectedNetSavings,
        BigDecimal projectedEndingBalance
) {}
