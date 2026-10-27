package com.fintrack.service;

/**
 * Exception thrown when an exchange rate provider fails to fetch or parse currency exchange rates.
 */
public class ExchangeRateException extends RuntimeException {

    public ExchangeRateException(String message) {
        super(message);
    }

    public ExchangeRateException(String message, Throwable cause) {
        super(message, cause);
    }
}
