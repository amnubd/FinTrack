package com.fintrack.service.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composite forecasting model delivering immediate rest-of-month projections
 * alongside multi-month forward liquidity trajectory.
 *
 * @param currentBalance                   real-time actual net cash balance across all historical records
 * @param projectedCurrentMonthEndBalance projected balance at the conclusion of the active calendar month
 * @param futureForecasts                  sequential forecasts for subsequent forward months (e.g., next 3 months)
 */
public record ForecastResult(
        BigDecimal currentBalance,
        BigDecimal projectedCurrentMonthEndBalance,
        List<MonthlyForecast> futureForecasts
) {}
