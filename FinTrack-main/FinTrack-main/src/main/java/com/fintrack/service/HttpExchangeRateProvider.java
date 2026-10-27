package com.fintrack.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Production exchange rate provider connecting over HTTPS to standard REST exchange rate services.
 * Default provider: open.er-api.com (ExchangeRate-API open tier).
 * Configurable via system property 'fintrack.exchangerate.url' or environment variable 'FINTRACK_EXCHANGERATE_URL'.
 * Optional API key via 'fintrack.exchangerate.apikey' or 'FINTRACK_EXCHANGERATE_API_KEY'.
 */
public class HttpExchangeRateProvider implements ExchangeRateProvider {

    public static final String DEFAULT_URL_TEMPLATE = "https://open.er-api.com/v6/latest/{base}";
    private static final String PROVIDER_NAME = "ExchangeRate-API";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final String urlTemplate;
    private final Duration timeout;

    public HttpExchangeRateProvider() {
        this(resolveEndpointUrl(), DEFAULT_TIMEOUT);
    }

    public HttpExchangeRateProvider(String urlTemplate, Duration timeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
                urlTemplate, timeout);
    }

    public HttpExchangeRateProvider(HttpClient httpClient, String urlTemplate, Duration timeout) {
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newHttpClient();
        this.urlTemplate = (urlTemplate != null && !urlTemplate.isBlank()) ? urlTemplate : DEFAULT_URL_TEMPLATE;
        this.timeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, BigDecimal> fetchLatestRates(String baseCurrency) throws ExchangeRateException {
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new ExchangeRateException("Base currency cannot be null or blank");
        }
        String cleanBase = baseCurrency.trim().toUpperCase();
        String requestUrl = urlTemplate.replace("{base}", cleanBase);

        String apiKey = resolveApiKey();
        if (apiKey != null && !apiKey.isBlank() && requestUrl.contains("{key}")) {
            requestUrl = requestUrl.replace("{key}", apiKey);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "FinTrack-PersonalFinance/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ExchangeRateException(String.format(
                        "Upstream exchange rate service returned HTTP %d for base currency %s",
                        response.statusCode(), cleanBase));
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new ExchangeRateException("Empty response body from exchange rate service");
            }

            Map<String, BigDecimal> rates = parseRatesFromJson(body);
            if (rates.isEmpty()) {
                throw new ExchangeRateException("No rates found in upstream exchange rate response for " + cleanBase);
            }

            rates.put(cleanBase, BigDecimal.ONE);
            return Collections.unmodifiableMap(rates);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExchangeRateException("Exchange rate request was interrupted", e);
        } catch (IOException e) {
            throw new ExchangeRateException("Network I/O error contacting exchange rate provider: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ExchangeRateException("Invalid request URI or configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the JSON rates object safely without third-party dependencies.
     */
    static Map<String, BigDecimal> parseRatesFromJson(String json) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (json == null) return result;

        int ratesIdx = json.indexOf("\"rates\"");
        if (ratesIdx == -1) {
            ratesIdx = json.indexOf("\"conversion_rates\"");
        }
        if (ratesIdx == -1) {
            return result;
        }

        int openBrace = json.indexOf('{', ratesIdx);
        if (openBrace == -1) return result;

        int closeBrace = json.indexOf('}', openBrace);
        if (closeBrace == -1) return result;

        String ratesContent = json.substring(openBrace + 1, closeBrace);
        String[] pairs = ratesContent.split(",");

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx == -1) continue;

            String keyPart = pair.substring(0, colonIdx).replace("\"", "").trim().toUpperCase();
            String valPart = pair.substring(colonIdx + 1).replace("\"", "").trim();

            if (!keyPart.isEmpty() && !valPart.isEmpty()) {
                try {
                    BigDecimal rate = new BigDecimal(valPart);
                    if (rate.compareTo(BigDecimal.ZERO) > 0) {
                        result.put(keyPart, rate);
                    }
                } catch (NumberFormatException ignored) {
                    // Skip invalid entry
                }
            }
        }
        return result;
    }

    private static String resolveEndpointUrl() {
        String env = System.getenv("FINTRACK_EXCHANGERATE_URL");
        if (env != null && !env.isBlank()) return env.trim();
        String prop = System.getProperty("fintrack.exchangerate.url");
        if (prop != null && !prop.isBlank()) return prop.trim();
        return DEFAULT_URL_TEMPLATE;
    }

    private static String resolveApiKey() {
        String env = System.getenv("FINTRACK_EXCHANGERATE_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        String prop = System.getProperty("fintrack.exchangerate.apikey");
        if (prop != null && !prop.isBlank()) return prop.trim();
        return null;
    }
}
