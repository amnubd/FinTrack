package com.fintrack.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity representing an exchange rate quotation between two currencies.
 */
public class ExchangeRate {

    private final String baseCurrency;
    private final String targetCurrency;
    private final BigDecimal rate;
    private final LocalDateTime timestamp;
    private final String provider;
    private final boolean cached;

    /**
     * Constructs an immutable ExchangeRate record.
     *
     * @param baseCurrency   ISO 4217 base currency (e.g., "USD")
     * @param targetCurrency ISO 4217 target currency (e.g., "INR")
     * @param rate           exchange multiplier; must be strictly positive (> 0)
     * @param timestamp      timestamp when quotation was retrieved/recorded
     * @param provider       source provider name (e.g., "ExchangeRate-API")
     * @param cached         whether this quotation is from local persistent cache
     */
    public ExchangeRate(String baseCurrency,
                        String targetCurrency,
                        BigDecimal rate,
                        LocalDateTime timestamp,
                        String provider,
                        boolean cached) {
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new ValidationException("Base currency cannot be null or blank.");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new ValidationException("Target currency cannot be null or blank.");
        }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Exchange rate must be strictly positive (> 0).");
        }
        this.baseCurrency = baseCurrency.trim().toUpperCase();
        this.targetCurrency = targetCurrency.trim().toUpperCase();
        this.rate = rate;
        this.timestamp = (timestamp != null) ? timestamp : LocalDateTime.now();
        this.provider = (provider != null && !provider.isBlank()) ? provider.trim() : "Unknown";
        this.cached = cached;
    }

    public ExchangeRate(String baseCurrency,
                        String targetCurrency,
                        BigDecimal rate,
                        LocalDateTime timestamp,
                        String provider) {
        this(baseCurrency, targetCurrency, rate, timestamp, provider, false);
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getRetrievalTimestamp() {
        return timestamp;
    }

    public String getProvider() {
        return provider;
    }

    public boolean isCached() {
        return cached;
    }

    /**
     * Converts an amount from base currency to target currency using this rate.
     *
     * @param amount monetary amount in base currency
     * @param scale  decimal precision
     * @return converted amount in target currency rounded with HALF_UP
     */
    public BigDecimal convert(BigDecimal amount, int scale) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(rate).setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExchangeRate that)) return false;
        return baseCurrency.equalsIgnoreCase(that.baseCurrency)
                && targetCurrency.equalsIgnoreCase(that.targetCurrency)
                && rate.compareTo(that.rate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCurrency.toUpperCase(), targetCurrency.toUpperCase(), rate.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return String.format("1 %s = %s %s [%s, %s%s]",
                baseCurrency, rate.toPlainString(), targetCurrency,
                provider, timestamp, cached ? " (Cached)" : "");
    }
}
