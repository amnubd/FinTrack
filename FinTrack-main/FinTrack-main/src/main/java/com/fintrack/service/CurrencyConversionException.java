package com.fintrack.service;

/**
 * Exception thrown when a monetary currency conversion cannot be evaluated due to missing or unsupported rates.
 */
public class CurrencyConversionException extends RuntimeException {

    public CurrencyConversionException(String message) {
        super(message);
    }

    public CurrencyConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
