package com.fintrack.service;

import com.fintrack.model.Category;
import com.fintrack.model.ExchangeRate;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.storage.CsvStorageEngine;
import com.fintrack.storage.SQLiteStorageEngine;
import com.fintrack.util.CurrencyFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-grade test suite verifying real currency conversion, precision, rounding,
 * persistent caching, offline fallback, and multi-currency financial accounting.
 */
class CurrencyConversionServiceTest {

    @TempDir
    Path tempDir;

    private SQLiteStorageEngine storageEngine;
    private MockExchangeRateProvider mockProvider;
    private CurrencyConversionService conversionService;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_currencies.db").toAbsolutePath().toString();
        storageEngine = new SQLiteStorageEngine(dbPath);

        mockProvider = new MockExchangeRateProvider();
        // Setup realistic test quotations
        mockProvider.setExchangeRate("USD", "INR", new BigDecimal("83.250000"));
        mockProvider.setExchangeRate("USD", "EUR", new BigDecimal("0.920000"));
        mockProvider.setExchangeRate("USD", "GBP", new BigDecimal("0.780000"));
        mockProvider.setExchangeRate("USD", "JPY", new BigDecimal("155.500000"));
        mockProvider.setExchangeRate("EUR", "JPY", new BigDecimal("169.021739"));

        conversionService = new CurrencyConversionService(mockProvider, storageEngine);
    }

    @AfterEach
    void tearDown() {
        if (storageEngine != null) {
            storageEngine.close();
        }
    }

    @Test
    @DisplayName("Convert between standard currencies with 2 decimal places")
    void testStandardCurrencyConversion() {
        // 100 USD -> INR = 8325.00 INR
        BigDecimal inr = conversionService.convert(new BigDecimal("100.00"), "USD", "INR");
        assertEquals(new BigDecimal("8325.00"), inr);

        // 50 USD -> EUR = 46.00 EUR
        BigDecimal eur = conversionService.convert(new BigDecimal("50.00"), "USD", "EUR");
        assertEquals(new BigDecimal("46.00"), eur);
    }

    @Test
    @DisplayName("Same currency conversion returns identity with correct scale")
    void testIdentityConversion() {
        BigDecimal usd = conversionService.convert(new BigDecimal("123.456"), "USD", "USD");
        assertEquals(new BigDecimal("123.46"), usd);

        BigDecimal jpy = conversionService.convert(new BigDecimal("123.456"), "JPY", "JPY");
        assertEquals(new BigDecimal("123"), jpy);
    }

    @Test
    @DisplayName("JPY requires 0 fraction digits and HALF_UP rounding")
    void testJpyZeroFractionDigitsPrecision() {
        // 10.50 USD * 155.5 = 1632.75 -> rounded HALF_UP = 1633 JPY
        BigDecimal jpy = conversionService.convert(new BigDecimal("10.50"), "USD", "JPY");
        assertEquals(new BigDecimal("1633"), jpy);
        assertEquals(0, jpy.scale());

        // 10.10 USD * 155.5 = 1570.55 -> rounded HALF_UP = 1571 JPY
        BigDecimal jpy2 = conversionService.convert(new BigDecimal("10.10"), "USD", "JPY");
        assertEquals(new BigDecimal("1571"), jpy2);
        assertEquals(0, jpy2.scale());
    }

    @Test
    @DisplayName("Inverse rate calculation when direct pair not directly published")
    void testInverseRateCalculation() {
        // Only USD -> INR is set in persistent DB
        storageEngine.saveExchangeRate(new ExchangeRate("USD", "INR", new BigDecimal("80.000000"), LocalDateTime.now(), "TestProvider"));

        // Disable mock provider to force storage cache fallback
        mockProvider.clear();

        // 800 INR -> USD should be 10.00 USD (1 / 80 = 0.0125 * 800 = 10.00)
        BigDecimal usd = conversionService.convert(new BigDecimal("800.00"), "INR", "USD");
        assertEquals(new BigDecimal("10.00"), usd);
    }

    @Test
    @DisplayName("Triangulation via USD cross-rate when direct rate not available")
    void testCrossRateTriangulationViaUsd() {
        // Store USD -> EUR (0.90) and USD -> INR (81.00) in database
        storageEngine.saveExchangeRate(new ExchangeRate("USD", "EUR", new BigDecimal("0.900000"), LocalDateTime.now(), "Test"));
        storageEngine.saveExchangeRate(new ExchangeRate("USD", "INR", new BigDecimal("81.000000"), LocalDateTime.now(), "Test"));

        // Disable mock provider
        mockProvider.clear();

        // Cross rate EUR -> INR = 81.00 / 0.90 = 90.00
        // 100 EUR -> 9000.00 INR
        BigDecimal inr = conversionService.convert(new BigDecimal("100.00"), "EUR", "INR");
        assertEquals(new BigDecimal("9000.00"), inr);
    }

    @Test
    @DisplayName("Persistent caching saves retrieved rates to SQLite and reloads them offline")
    void testPersistentCachingToSqlite() {
        // Fetch rate USD -> EUR through conversion service
        conversionService.convert(new BigDecimal("10.00"), "USD", "EUR");

        // Verify SQLite table exchange_rates has it stored
        Optional<ExchangeRate> cachedInDb = storageEngine.getExchangeRate("USD", "EUR");
        assertTrue(cachedInDb.isPresent());
        assertEquals(0, new BigDecimal("0.92").compareTo(cachedInDb.get().getRate()));

        // Create new service with failing provider (offline simulation)
        ExchangeRateProvider failingProvider = baseCurrency -> {
            throw new ExchangeRateException("Simulated Network Failure");
        };
        CurrencyConversionService offlineService = new CurrencyConversionService(failingProvider, storageEngine);

        // Should successfully convert using SQLite persistent cache!
        BigDecimal offlineResult = offlineService.convert(new BigDecimal("100.00"), "USD", "EUR");
        assertEquals(new BigDecimal("92.00"), offlineResult);
    }

    @Test
    @DisplayName("Unavailable rate throws CurrencyConversionException and does NOT assume 1.0")
    void testUnavailableRateThrowsException() {
        mockProvider.clear(); // Empty provider

        CurrencyConversionException exception = assertThrows(CurrencyConversionException.class, () ->
                conversionService.convert(new BigDecimal("100.00"), "USD", "ZWL")
        );
        assertTrue(exception.getMessage().contains("currently unavailable"));
    }

    @Test
    @DisplayName("AnalyticsProcessor properly aggregates transactions with mixed currencies")
    void testMultiCurrencyAnalyticsAggregation() {
        User user = new User("u_multi", "Multi Currency User", "multi@fintrack.org", "USD",
                new BigDecimal("5000.00"), new BigDecimal("2000.00"), new BigDecimal("1000.00"));
        user.setReportingCurrency("USD");
        storageEngine.saveUser(user);

        // Transaction 1: USD 100 Expense
        Transaction tx1 = new Transaction("tx1", user.getId(), LocalDateTime.now(),
                Category.FOOD_AND_DINING, new BigDecimal("100.00"), "USD",
                PaymentMode.CARD, TransactionType.EXPENSE, "Lunch");
        storageEngine.saveTransaction(tx1);

        // Transaction 2: EUR 92 Expense (should equal USD 100.00 at rate 0.92 EUR/USD)
        // Rate is USD->EUR = 0.92. Inverse EUR->USD is 1 / 0.92 = 1.0869565 * 92 = 100.00 USD!
        Transaction tx2 = new Transaction("tx2", user.getId(), LocalDateTime.now(),
                Category.ENTERTAINMENT, new BigDecimal("92.00"), "EUR",
                PaymentMode.CARD, TransactionType.EXPENSE, "Cinema");
        storageEngine.saveTransaction(tx2);

        // Transaction 3: INR 8325 Income (should equal USD 100.00 at rate 83.25 INR/USD)
        Transaction tx3 = new Transaction("tx3", user.getId(), LocalDateTime.now(),
                Category.SALARY, new BigDecimal("8325.00"), "INR",
                PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Bonus");
        storageEngine.saveTransaction(tx3);

        BudgetManager bm = new BudgetManager(storageEngine, conversionService);
        AnalyticsProcessor analytics = new AnalyticsProcessor(storageEngine, bm, conversionService);

        // Total Expenses in USD should be ~200.00 USD (tx1: 100 + tx2: 100)
        // Total Income in USD should be ~100.00 USD (tx3: 100)
        // Current Balance should be Income (100) - Expenses (200) = -100.00 USD
        BigDecimal balance = analytics.computeCurrentBalance(user.getId());
        assertEquals(new BigDecimal("-100.00"), balance);
    }

    @Test
    @DisplayName("Goal contribution with currency preserves goal currency and transaction currency")
    void testGoalCurrencyPreservation() {
        User user = new User("u_goal", "Goal User", "goal@fintrack.org", "INR",
                new BigDecimal("100000.00"), new BigDecimal("40000.00"), new BigDecimal("20000.00"));
        storageEngine.saveUser(user);

        BudgetManager bm = new BudgetManager(storageEngine, conversionService);
        Goal goal = bm.createGoal(user.getId(), "Japan Trip", new BigDecimal("300000"), "JPY", LocalDate.now().plusMonths(6));
        assertEquals("JPY", goal.getCurrency());

        bm.contributeToGoal(goal.getId(), new BigDecimal("50000"), PaymentMode.BANK_TRANSFER);

        Goal updated = storageEngine.getGoalById(goal.getId()).orElseThrow();
        assertEquals(new BigDecimal("50000"), updated.getCurrentAmount());

        List<Transaction> txs = storageEngine.getTransactionsByUserId(user.getId());
        assertEquals(1, txs.size());
        assertEquals("JPY", txs.get(0).getCurrency());
        assertEquals(new BigDecimal("50000"), txs.get(0).getAmount());
    }

    @Test
    @DisplayName("CSV export and import round-trip preserves currency and reportingCurrency")
    void testCsvExportAndImportWithCurrency() {
        Path exportDir = tempDir.resolve("csv_export");

        User user = new User("u_csv", "CSV User", "csv@fintrack.org", "INR",
                new BigDecimal("100000.00"), new BigDecimal("40000.00"), new BigDecimal("20000.00"));
        user.setReportingCurrency("EUR");
        storageEngine.saveUser(user);

        Transaction tx = new Transaction("tx_csv", user.getId(), LocalDateTime.now(),
                Category.SHOPPING, new BigDecimal("49.99"), "GBP",
                PaymentMode.CARD, TransactionType.EXPENSE, "Coat");
        storageEngine.saveTransaction(tx);

        storageEngine.exportToCsv(exportDir.toAbsolutePath().toString());

        // Import into new in-memory SQLite database
        String importDbPath = tempDir.resolve("imported.db").toAbsolutePath().toString();
        try (SQLiteStorageEngine importEngine = new SQLiteStorageEngine(importDbPath)) {
            importEngine.importFromCsv(exportDir.toAbsolutePath().toString());

            User importedUser = importEngine.getUserById("u_csv").orElseThrow();
            assertEquals("INR", importedUser.getCurrency());
            assertEquals("EUR", importedUser.getReportingCurrency());

            Transaction importedTx = importEngine.getTransactionById("tx_csv").orElseThrow();
            assertEquals("GBP", importedTx.getCurrency());
            assertEquals(new BigDecimal("49.99"), importedTx.getAmount());
        }
    }
}
