package com.fintrack.util;

import com.fintrack.model.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

/**
 * Utility for formatting and parsing monetary amounts based on dynamic user currency settings.
 */
public final class CurrencyFormatter {

    private static final Map<String, String> SYMBOL_MAP = Map.of(
            "USD", "$",
            "INR", "₹",
            "EUR", "€",
            "GBP", "£",
            "JPY", "¥",
            "CAD", "C$",
            "AUD", "A$"
    );

    private CurrencyFormatter() {}

    /**
     * Resolves the display symbol for a given currency code.
     *
     * @param currencyCode ISO 4217 currency code (e.g., "USD", "INR", "EUR")
     * @return symbol string (e.g., "$", "₹", "€")
     */
    public static String getCurrencySymbol(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "$";
        }
        String cleanCode = currencyCode.trim().toUpperCase();
        if (SYMBOL_MAP.containsKey(cleanCode)) {
            return SYMBOL_MAP.get(cleanCode);
        }
        try {
            return Currency.getInstance(cleanCode).getSymbol(Locale.getDefault());
        } catch (Exception e) {
            return cleanCode + " ";
        }
    }

    /**
     * Resolves standard fractional decimal places for a currency (e.g., JPY has 0, USD/INR/EUR has 2).
     */
    public static int getFractionDigits(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return 2;
        }
        String cleanCode = currencyCode.trim().toUpperCase();
        if ("JPY".equals(cleanCode)) {
            return 0;
        }
        try {
            int digits = Currency.getInstance(cleanCode).getDefaultFractionDigits();
            return digits >= 0 ? digits : 2;
        } catch (Exception e) {
            return 2;
        }
    }

    /**
     * Formats an amount with the currency symbol and appropriate decimal places.
     * Example: format(1250.5, "INR") -> "₹1250.50", format(1500, "JPY") -> "¥1500"
     */
    public static String format(BigDecimal amount, String currencyCode) {
        String symbol = getCurrencySymbol(currencyCode);
        BigDecimal val = (amount == null) ? BigDecimal.ZERO : amount;
        int scale = getFractionDigits(currencyCode);
        return symbol + val.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Formats an amount with a sign prefix based on transaction type.
     * INCOME -> "+<symbol><amount>"
     * EXPENSE -> "-<symbol><amount>"
     */
    public static String formatTransactionAmount(BigDecimal amount, TransactionType type, String currencyCode) {
        String symbol = getCurrencySymbol(currencyCode);
        BigDecimal val = (amount == null) ? BigDecimal.ZERO : amount;
        String prefix = (type == TransactionType.INCOME) ? "+" : "-";
        int scale = getFractionDigits(currencyCode);
        return prefix + symbol + val.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Formats an amount with explicit sign prefix for net balance.
     * >= 0 -> "+<symbol><amount>"
     * < 0  -> "-<symbol><amount.abs()>"
     */
    public static String formatSignedBalance(BigDecimal balance, String currencyCode) {
        String symbol = getCurrencySymbol(currencyCode);
        BigDecimal val = (balance == null) ? BigDecimal.ZERO : balance;
        String prefix = (val.signum() >= 0) ? "+" : "-";
        int scale = getFractionDigits(currencyCode);
        return prefix + symbol + val.abs().setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Parses a formatted currency string back into a numeric BigDecimal for sorting.
     */
    public static BigDecimal parseAmount(String formatted) {
        if (formatted == null || formatted.isBlank()) {
            return BigDecimal.ZERO;
        }
        String s = formatted.trim();
        boolean isNegative = s.startsWith("-") || s.contains("-$");
        StringBuilder digitsAndDot = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '.') {
                digitsAndDot.append(c);
            } else if (c == '-' && digitsAndDot.length() == 0) {
                isNegative = true;
            }
        }
        if (digitsAndDot.length() == 0) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal bd = new BigDecimal(digitsAndDot.toString());
            return isNegative ? bd.negate() : bd;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Comparator for sorting formatted currency strings numerically.
     */
    public static int compareFormattedAmounts(String s1, String s2) {
        BigDecimal b1 = parseAmount(s1);
        BigDecimal b2 = parseAmount(s2);
        return b1.compareTo(b2);
    }
}
