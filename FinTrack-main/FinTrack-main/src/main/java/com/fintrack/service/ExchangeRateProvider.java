package com.fintrack.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Strategy interface for providers that fetch foreign exchange quotations.
 */
public interface ExchangeRateProvider {

    /**
     * Identifies the service or upstream API provider (e.g., "ExchangeRate-API", "Frankfurter", "Mock").
     */
    default String getProviderName() {
        return getClass().getSimpleName();
    }

    /**
     * Fetches current exchange rates where the specified base currency has a value of 1.0.
     *
     * @param baseCurrency ISO 4217 standard currency code (e.g., "USD")
     * @return map of target ISO currency codes to exchange rates
     * @throws ExchangeRateException on network timeout, API error, or unparseable response
     */
    Map<String, BigDecimal> fetchLatestRates(String baseCurrency) throws ExchangeRateException;
}
