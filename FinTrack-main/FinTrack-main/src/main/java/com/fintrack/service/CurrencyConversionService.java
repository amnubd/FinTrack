package com.fintrack.service;

import com.fintrack.model.ExchangeRate;
import com.fintrack.storage.StorageEngine;
import com.fintrack.util.CurrencyFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized service managing foreign exchange rates, persistent caching, and
 * mathematically rigorous, high-precision currency conversions across FinTrack.
 */
public class CurrencyConversionService {

    private static final Duration CACHE_TTL = Duration.ofHours(12);
    private static final int INTERNAL_CALCULATION_SCALE = 12;

    private final StorageEngine storageEngine;
    private ExchangeRateProvider provider;
    private final Map<String, Map<String, ExchangeRate>> memoryCache = new ConcurrentHashMap<>();

    public CurrencyConversionService(StorageEngine storageEngine) {
        this(new HttpExchangeRateProvider(), storageEngine);
    }

    public CurrencyConversionService(ExchangeRateProvider provider, StorageEngine storageEngine) {
        this.provider = Objects.requireNonNull(provider, "ExchangeRateProvider cannot be null");
        this.storageEngine = Objects.requireNonNull(storageEngine, "StorageEngine cannot be null");
    }

    public synchronized ExchangeRateProvider getProvider() {
        return provider;
    }

    public synchronized void setProvider(ExchangeRateProvider provider) {
        this.provider = Objects.requireNonNull(provider, "ExchangeRateProvider cannot be null");
        this.memoryCache.clear();
    }

    /**
     * Converts a monetary amount from one currency to another using the best available live or cached rate.
     *
     * @param amount       monetary amount to convert; null defaults to ZERO
     * @param fromCurrency source ISO 4217 currency code
     * @param toCurrency   target ISO 4217 currency code
     * @return converted amount rounded with HALF_UP according to target currency's standard fraction digits
     * @throws CurrencyConversionException if no exchange rate can be resolved
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        int targetScale = CurrencyFormatter.getFractionDigits(to);

        if (from.equalsIgnoreCase(to)) {
            return amount.setScale(targetScale, RoundingMode.HALF_UP);
        }

        ExchangeRate exchangeRate = getExchangeRate(from, to);
        return exchangeRate.convert(amount, targetScale);
    }

    /**
     * Retrieves the active exchange rate between two currencies.
     * Checks memory cache, persistent storage cache, upstream network provider, and triangulation.
     *
     * @param fromCurrency source currency
     * @param toCurrency   destination currency
     * @return valid ExchangeRate entity
     * @throws CurrencyConversionException if rate is unavailable
     */
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);

        if (from.equalsIgnoreCase(to)) {
            return new ExchangeRate(from, to, BigDecimal.ONE, LocalDateTime.now(), "Identity", false);
        }

        // 1. Check in-memory cache
        ExchangeRate memRate = getFromMemory(from, to);
        if (memRate != null && isFresh(memRate)) {
            return memRate;
        }

        // 2. Check persistent storage cache
        Optional<ExchangeRate> dbRate = storageEngine.getExchangeRate(from, to);
        if (dbRate.isPresent() && isFresh(dbRate.get())) {
            putInMemory(dbRate.get());
            return dbRate.get();
        }

        // 3. Attempt upstream live refresh
        try {
            refreshRates(from);
            ExchangeRate refreshed = getFromMemory(from, to);
            if (refreshed != null) {
                return refreshed;
            }
        } catch (ExchangeRateException ignored) {
            // Upstream failed or offline; continue to cached fallbacks
        }

        // 4. Fall back to cached rate even if expired
        if (memRate != null) {
            return markAsCached(memRate);
        }
        if (dbRate.isPresent()) {
            ExchangeRate cached = markAsCached(dbRate.get());
            putInMemory(cached);
            return cached;
        }

        // 5. Try inverse rate (to -> from)
        Optional<ExchangeRate> inverse = storageEngine.getExchangeRate(to, from);
        if (inverse.isPresent()) {
            BigDecimal invRate = BigDecimal.ONE.divide(inverse.get().getRate(), INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);
            ExchangeRate derived = new ExchangeRate(from, to, invRate, inverse.get().getTimestamp(), inverse.get().getProvider() + " (Inverted)", true);
            putInMemory(derived);
            return derived;
        }

        // 6. Try cross-rate triangulation via USD
        if (!"USD".equalsIgnoreCase(from) && !"USD".equalsIgnoreCase(to)) {
            Optional<BigDecimal> fromToUsd = resolveRateAgainstUsd(from, true);
            Optional<BigDecimal> usdToTarget = resolveRateAgainstUsd(to, false);

            if (fromToUsd.isPresent() && usdToTarget.isPresent()) {
                BigDecimal crossRate = fromToUsd.get().multiply(usdToTarget.get()).setScale(INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);
                ExchangeRate derived = new ExchangeRate(from, to, crossRate, LocalDateTime.now(), "USD Triangulation", true);
                putInMemory(derived);
                return derived;
            }
        }

        throw new CurrencyConversionException(String.format(
                "Exchange rate between %s and %s is currently unavailable (network unreachable and no cached rate found).",
                from, to));
    }

    private Optional<BigDecimal> resolveRateAgainstUsd(String currency, boolean toUsd) {
        if ("USD".equalsIgnoreCase(currency)) {
            return Optional.of(BigDecimal.ONE);
        }
        String first = toUsd ? currency : "USD";
        String second = toUsd ? "USD" : currency;

        ExchangeRate mem = getFromMemory(first, second);
        if (mem != null) return Optional.of(mem.getRate());

        Optional<ExchangeRate> db = storageEngine.getExchangeRate(first, second);
        if (db.isPresent()) return Optional.of(db.get().getRate());

        Optional<ExchangeRate> inv = storageEngine.getExchangeRate(second, first);
        if (inv.isPresent()) {
            return Optional.of(BigDecimal.ONE.divide(inv.get().getRate(), INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP));
        }

        ExchangeRate memInv = getFromMemory(second, first);
        if (memInv != null) {
            return Optional.of(BigDecimal.ONE.divide(memInv.getRate(), INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP));
        }

        return Optional.empty();
    }

    /**
     * Refreshes exchange rates for a base currency from the active provider and stores them in cache.
     *
     * @param baseCurrency base ISO currency code
     * @throws ExchangeRateException if fetch fails
     */
    public synchronized void refreshRates(String baseCurrency) throws ExchangeRateException {
        String base = normalize(baseCurrency);
        Map<String, BigDecimal> latest = provider.fetchLatestRates(base);
        LocalDateTime now = LocalDateTime.now();
        String providerName = provider.getProviderName();

        for (Map.Entry<String, BigDecimal> entry : latest.entrySet()) {
            String target = normalize(entry.getKey());
            BigDecimal rateVal = entry.getValue();
            if (rateVal != null && rateVal.compareTo(BigDecimal.ZERO) > 0) {
                ExchangeRate rate = new ExchangeRate(base, target, rateVal, now, providerName, false);
                storageEngine.saveExchangeRate(rate);
                putInMemory(rate);

                if (!base.equalsIgnoreCase(target)) {
                    BigDecimal invRate = BigDecimal.ONE.divide(rateVal, INTERNAL_CALCULATION_SCALE, RoundingMode.HALF_UP);
                    ExchangeRate inv = new ExchangeRate(target, base, invRate, now, providerName, false);
                    storageEngine.saveExchangeRate(inv);
                    putInMemory(inv);
                }
            }
        }
    }

    /**
     * Checks if a rate is resolvable (either in memory, storage, or via triangulation).
     */
    public boolean hasRate(String fromCurrency, String toCurrency) {
        String from = normalize(fromCurrency);
        String to = normalize(toCurrency);
        if (from.equalsIgnoreCase(to)) return true;
        try {
            getExchangeRate(from, to);
            return true;
        } catch (CurrencyConversionException e) {
            return false;
        }
    }

    private boolean isFresh(ExchangeRate rate) {
        if (rate == null || rate.getTimestamp() == null) return false;
        Duration age = Duration.between(rate.getTimestamp(), LocalDateTime.now());
        return !age.isNegative() && age.compareTo(CACHE_TTL) <= 0;
    }

    private ExchangeRate markAsCached(ExchangeRate original) {
        return new ExchangeRate(
                original.getBaseCurrency(),
                original.getTargetCurrency(),
                original.getRate(),
                original.getTimestamp(),
                original.getProvider(),
                true
        );
    }

    private ExchangeRate getFromMemory(String base, String target) {
        Map<String, ExchangeRate> targetMap = memoryCache.get(base);
        return (targetMap != null) ? targetMap.get(target) : null;
    }

    private void putInMemory(ExchangeRate rate) {
        memoryCache.computeIfAbsent(rate.getBaseCurrency(), k -> new ConcurrentHashMap<>())
                .put(rate.getTargetCurrency(), rate);
    }

    private static String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        return currency.trim().toUpperCase();
    }
}
