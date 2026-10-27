package com.fintrack.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic mock exchange rate provider for offline automated tests.
 */
public class MockExchangeRateProvider implements ExchangeRateProvider {

    private final Map<String, Map<String, BigDecimal>> rateMatrix = new HashMap<>();
    private boolean shouldThrow = false;
    private String errorMessage = "Simulated provider failure";

    public MockExchangeRateProvider() {
        seedDefaultRates();
    }

    private void seedDefaultRates() {
        // Base USD
        Map<String, BigDecimal> usdRates = new HashMap<>();
        usdRates.put("USD", BigDecimal.ONE);
        usdRates.put("INR", new BigDecimal("83.50"));
        usdRates.put("EUR", new BigDecimal("0.92"));
        usdRates.put("GBP", new BigDecimal("0.78"));
        usdRates.put("JPY", new BigDecimal("152.00"));
        usdRates.put("CAD", new BigDecimal("1.38"));
        usdRates.put("AUD", new BigDecimal("1.52"));
        rateMatrix.put("USD", usdRates);

        // Derive INR base
        Map<String, BigDecimal> inrRates = new HashMap<>();
        inrRates.put("INR", BigDecimal.ONE);
        inrRates.put("USD", BigDecimal.ONE.divide(new BigDecimal("83.50"), 8, RoundingMode.HALF_UP));
        inrRates.put("EUR", new BigDecimal("0.92").divide(new BigDecimal("83.50"), 8, RoundingMode.HALF_UP));
        inrRates.put("GBP", new BigDecimal("0.78").divide(new BigDecimal("83.50"), 8, RoundingMode.HALF_UP));
        inrRates.put("JPY", new BigDecimal("152.00").divide(new BigDecimal("83.50"), 8, RoundingMode.HALF_UP));
        rateMatrix.put("INR", inrRates);

        // Derive EUR base
        Map<String, BigDecimal> eurRates = new HashMap<>();
        eurRates.put("EUR", BigDecimal.ONE);
        eurRates.put("USD", BigDecimal.ONE.divide(new BigDecimal("0.92"), 8, RoundingMode.HALF_UP));
        eurRates.put("INR", new BigDecimal("83.50").divide(new BigDecimal("0.92"), 8, RoundingMode.HALF_UP));
        eurRates.put("GBP", new BigDecimal("0.78").divide(new BigDecimal("0.92"), 8, RoundingMode.HALF_UP));
        eurRates.put("JPY", new BigDecimal("152.00").divide(new BigDecimal("0.92"), 8, RoundingMode.HALF_UP));
        rateMatrix.put("EUR", eurRates);
    }

    public void setRate(String base, String target, BigDecimal rate) {
        String b = base.toUpperCase();
        String t = target.toUpperCase();
        rateMatrix.computeIfAbsent(b, k -> new HashMap<>()).put(t, rate);
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal inv = BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP);
            rateMatrix.computeIfAbsent(t, k -> new HashMap<>()).put(b, inv);
        }
    }

    public void setExchangeRate(String base, String target, BigDecimal rate) {
        setRate(base, target, rate);
    }

    public void clear() {
        rateMatrix.clear();
    }

    public void setShouldThrow(boolean shouldThrow, String message) {
        this.shouldThrow = shouldThrow;
        this.errorMessage = message;
    }

    @Override
    public String getProviderName() {
        return "MockExchangeRateProvider";
    }

    @Override
    public Map<String, BigDecimal> fetchLatestRates(String baseCurrency) throws ExchangeRateException {
        if (shouldThrow) {
            throw new ExchangeRateException(errorMessage);
        }
        String base = baseCurrency.toUpperCase();
        Map<String, BigDecimal> rates = rateMatrix.get(base);
        if (rates == null) {
            // Triangulate via USD if available
            Map<String, BigDecimal> usdRates = rateMatrix.get("USD");
            if (usdRates != null && usdRates.containsKey(base)) {
                BigDecimal usdToBase = usdRates.get(base);
                Map<String, BigDecimal> derived = new HashMap<>();
                for (Map.Entry<String, BigDecimal> e : usdRates.entrySet()) {
                    derived.put(e.getKey(), e.getValue().divide(usdToBase, 8, RoundingMode.HALF_UP));
                }
                derived.put(base, BigDecimal.ONE);
                return Collections.unmodifiableMap(derived);
            }
            throw new ExchangeRateException("No mock rates configured for base currency: " + baseCurrency);
        }
        return Collections.unmodifiableMap(rates);
    }
}
