package com.fintrack.service;

import com.fintrack.model.*;
import com.fintrack.storage.SQLiteStorageEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent audit verification test suite validating:
 * 1. Legacy SQLite schema migration & rerun idempotency
 * 2. Immutable financial records under base/reporting currency changes
 * 3. Exact conservation of net worth in goal contributions without double-counting
 * 4. Resilient JSON exchange-rate parsing (rates & conversion_rates)
 * 5. Fail-fast safety when rates are unavailable (never assume 1.0)
 * 6. Triangulation and inverse rate mathematical precision
 */
class CurrencyFeatureAuditTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Audit 1: Legacy SQLite schema migration is safe, non-destructive, and idempotent")
    void testLegacyDatabaseMigrationAndIdempotency() throws Exception {
        String dbPath = tempDir.resolve("legacy_test.db").toAbsolutePath().toString();
        String jdbcUrl = "jdbc:sqlite:" + dbPath;

        // Step 1: Create a simulated legacy schema lacking currency columns
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE users (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    currency TEXT NOT NULL DEFAULT 'INR',
                    monthly_income NUMERIC NOT NULL DEFAULT 50000.0,
                    global_budget NUMERIC NOT NULL DEFAULT 30000.0,
                    savings_goal NUMERIC NOT NULL DEFAULT 10000.0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
            """);
            stmt.execute("""
                CREATE TABLE transactions (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    category TEXT NOT NULL,
                    amount NUMERIC NOT NULL,
                    payment_mode TEXT NOT NULL,
                    type TEXT NOT NULL,
                    note TEXT
                );
            """);
            stmt.execute("""
                CREATE TABLE budgets (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    monthly_limit NUMERIC NOT NULL,
                    month INTEGER NOT NULL,
                    year INTEGER NOT NULL
                );
            """);
            stmt.execute("""
                CREATE TABLE goals (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    target_amount NUMERIC NOT NULL,
                    current_amount NUMERIC NOT NULL DEFAULT 0.0,
                    target_date TEXT NOT NULL
                );
            """);

            // Seed legacy data
            stmt.execute("INSERT INTO users (id, name, email, currency) VALUES ('u_legacy', 'Legacy User', 'legacy@test.org', 'INR');");
            stmt.execute("INSERT INTO transactions (id, user_id, timestamp, category, amount, payment_mode, type, note) VALUES ('tx_leg1', 'u_legacy', '2026-01-15T10:00:00', 'FOOD_AND_DINING', 750.50, 'CARD', 'EXPENSE', 'Lunch');");
            stmt.execute("INSERT INTO budgets (id, user_id, category, monthly_limit, month, year) VALUES ('b_leg1', 'u_legacy', 'FOOD_AND_DINING', 5000.0, 1, 2026);");
            stmt.execute("INSERT INTO goals (id, user_id, name, target_amount, current_amount, target_date) VALUES ('g_leg1', 'u_legacy', 'Emergency', 100000.0, 25000.0, '2026-12-31');");
        }

        // Step 2: Initialize SQLiteStorageEngine which executes migration
        try (SQLiteStorageEngine engine = new SQLiteStorageEngine(dbPath)) {
            // Verify user migrated with reporting_currency = INR
            User u = engine.getUserById("u_legacy").orElseThrow();
            assertEquals("INR", u.getCurrency());
            assertEquals("INR", u.getReportingCurrency());

            // Verify legacy transaction inherited INR
            Transaction tx = engine.getTransactionById("tx_leg1").orElseThrow();
            assertEquals("INR", tx.getCurrency());
            assertEquals(0, new BigDecimal("750.5").compareTo(tx.getAmount()));

            // Verify legacy budget inherited INR
            Optional<Budget> b = engine.getBudgetByUserAndCategory("u_legacy", Category.FOOD_AND_DINING, 1, 2026);
            assertTrue(b.isPresent());
            assertEquals("INR", b.get().getCurrency());
            assertEquals(0, new BigDecimal("5000.0").compareTo(b.get().getMonthlyLimit()));

            // Verify legacy goal inherited INR
            Goal g = engine.getGoalById("g_leg1").orElseThrow();
            assertEquals("INR", g.getCurrency());
            assertEquals(0, new BigDecimal("100000.0").compareTo(g.getTargetAmount()));
            assertEquals(0, new BigDecimal("25000.0").compareTo(g.getCurrentAmount()));
        }

        // Step 3: Re-open database to verify migration rerun idempotency
        try (SQLiteStorageEngine engineRerun = new SQLiteStorageEngine(dbPath)) {
            User u = engineRerun.getUserById("u_legacy").orElseThrow();
            assertEquals("INR", u.getReportingCurrency());
            List<Transaction> txs = engineRerun.getAllTransactions();
            assertEquals(1, txs.size());
            assertEquals("tx_leg1", txs.get(0).getId());
            assertEquals("INR", txs.get(0).getCurrency());
        }
    }

    @Test
    @DisplayName("Audit 2: Changing reporting currency updates projections without mutating stored records")
    void testChangingReportingCurrencyPreservesStoredRecordsAndUpdatesProjections() {
        String dbPath = tempDir.resolve("reporting_currency_test.db").toAbsolutePath().toString();
        try (SQLiteStorageEngine engine = new SQLiteStorageEngine(dbPath)) {
            User user = new User("u_rep", "Report User", "rep@test.org", "INR",
                    new BigDecimal("50000.00"), new BigDecimal("30000.00"), new BigDecimal("10000.00"));
            user.setReportingCurrency("INR");
            engine.saveUser(user);

            // Save raw INR transaction of 8325.00
            Transaction tx = new Transaction("tx_rep1", user.getId(), LocalDateTime.now(),
                    Category.FOOD_AND_DINING, new BigDecimal("8325.00"), "INR",
                    PaymentMode.CARD, TransactionType.EXPENSE, "Family Dinner");
            engine.saveTransaction(tx);

            MockExchangeRateProvider mock = new MockExchangeRateProvider();
            mock.setExchangeRate("USD", "INR", new BigDecimal("83.250000"));
            CurrencyConversionService ccs = new CurrencyConversionService(mock, engine);
            BudgetManager bm = new BudgetManager(engine, ccs);
            AnalyticsProcessor ap = new AnalyticsProcessor(engine, bm, ccs);

            // Balance in INR reporting currency
            BigDecimal balanceInr = ap.computeCurrentBalance(user.getId());
            assertEquals(new BigDecimal("-8325.00"), balanceInr);

            // Change reporting currency to USD
            user.setReportingCurrency("USD");
            engine.updateUser(user);

            // Balance in USD reporting currency: -8325 INR / 83.25 = -100.00 USD
            BigDecimal balanceUsd = ap.computeCurrentBalance(user.getId());
            assertEquals(new BigDecimal("-100.00"), balanceUsd);

            // Critical Check: Verify stored transaction in SQLite is STILL exactly 8325.00 INR!
            Transaction storedTx = engine.getTransactionById("tx_rep1").orElseThrow();
            assertEquals(0, new BigDecimal("8325.00").compareTo(storedTx.getAmount()));
            assertEquals("INR", storedTx.getCurrency());

            // Switch reporting currency back to INR
            user.setReportingCurrency("INR");
            engine.updateUser(user);
            BigDecimal balanceInrRestored = ap.computeCurrentBalance(user.getId());
            assertEquals(new BigDecimal("-8325.00"), balanceInrRestored);
        }
    }

    @Test
    @DisplayName("Audit 3: Changing base account currency does not rewrite historical transactions")
    void testChangingAccountBaseCurrencyDoesNotCorruptHistoricalTransactions() {
        String dbPath = tempDir.resolve("base_currency_test.db").toAbsolutePath().toString();
        try (SQLiteStorageEngine engine = new SQLiteStorageEngine(dbPath)) {
            User user = new User("u_base", "Base User", "base@test.org", "EUR",
                    new BigDecimal("3000.00"), new BigDecimal("2000.00"), new BigDecimal("1000.00"));
            user.setReportingCurrency("EUR");
            engine.saveUser(user);

            Transaction tx = new Transaction("tx_eur1", user.getId(), LocalDateTime.now(),
                    Category.HOUSING, new BigDecimal("850.00"), "EUR",
                    PaymentMode.BANK_TRANSFER, TransactionType.EXPENSE, "Apartment Rent");
            engine.saveTransaction(tx);

            // User moves country and switches account base currency to USD
            user.setCurrency("USD");
            user.setReportingCurrency("USD");
            engine.updateUser(user);

            // Verify historical transaction in DB remains 850.00 EUR
            Transaction historical = engine.getTransactionById("tx_eur1").orElseThrow();
            assertEquals("EUR", historical.getCurrency());
            assertEquals(0, new BigDecimal("850.00").compareTo(historical.getAmount()));
        }
    }

    @Test
    @DisplayName("Audit 4: Savings goal contribution flow maintains strict net worth conservation")
    void testSavingsContributionConservationOfNetWorth() {
        String dbPath = tempDir.resolve("goal_conservation_test.db").toAbsolutePath().toString();
        try (SQLiteStorageEngine engine = new SQLiteStorageEngine(dbPath)) {
            User user = new User("u_goal_c", "Goal Conserve", "gc@test.org", "USD",
                    new BigDecimal("5000.00"), new BigDecimal("3000.00"), new BigDecimal("1000.00"));
            engine.saveUser(user);

            MockExchangeRateProvider mock = new MockExchangeRateProvider();
            CurrencyConversionService ccs = new CurrencyConversionService(mock, engine);
            BudgetManager bm = new BudgetManager(engine, ccs);
            AnalyticsProcessor ap = new AnalyticsProcessor(engine, bm, ccs);

            // Initial Income: $10,000
            engine.saveTransaction(new Transaction("tx_inc", user.getId(), LocalDateTime.now(),
                    Category.SALARY, new BigDecimal("10000.00"), "USD",
                    PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Monthly Inflow"));

            // Initial cash balance = 10,000
            assertEquals(new BigDecimal("10000.00"), ap.computeCurrentBalance(user.getId()));

            // Create goal: $5,000 target
            Goal goal = bm.createGoal(user.getId(), "Car Fund", new BigDecimal("5000.00"), LocalDate.now().plusMonths(6));

            // Initial wealth: Cash (10,000) + Goals (0) = 10,000
            BigDecimal initialWealth = ap.computeCurrentBalance(user.getId()).add(goal.getCurrentAmount());
            assertEquals(new BigDecimal("10000.00"), initialWealth);

            // Contribute $2,500 to goal
            bm.contributeToGoal(goal.getId(), new BigDecimal("2500.00"), PaymentMode.BANK_TRANSFER);

            // Cash balance after contribution: 10,000 - 2,500 = 7,500
            BigDecimal cashAfter = ap.computeCurrentBalance(user.getId());
            assertEquals(new BigDecimal("7500.00"), cashAfter);

            // Goal progress after contribution: 2,500
            Goal updatedGoal = engine.getGoalById(goal.getId()).orElseThrow();
            assertEquals(0, new BigDecimal("2500.00").compareTo(updatedGoal.getCurrentAmount()));

            // Total Wealth = Cash (7,500) + Goals (2,500) = 10,000.00
            BigDecimal postWealth = cashAfter.add(updatedGoal.getCurrentAmount());
            assertEquals(new BigDecimal("10000.00"), postWealth);
        }
    }

    @Test
    @DisplayName("Audit 5: HttpExchangeRateProvider parses standard rates and v6 conversion_rates")
    void testHttpExchangeRateProviderJsonParsing() {
        // Standard open.er-api.com format
        String openFormat = """
            {
                "result": "success",
                "base_code": "USD",
                "rates": {
                    "USD": 1.0,
                    "EUR": 0.92,
                    "INR": 83.25
                }
            }
        """;
        Map<String, BigDecimal> openRates = HttpExchangeRateProvider.parseRatesFromJson(openFormat);
        assertEquals(3, openRates.size());
        assertEquals(new BigDecimal("0.92"), openRates.get("EUR"));
        assertEquals(new BigDecimal("83.25"), openRates.get("INR"));

        // Standard v6 exchangerate-api format
        String v6Format = """
            {
                "result": "success",
                "base_code": "USD",
                "conversion_rates": {
                    "USD": 1.0,
                    "GBP": 0.78,
                    "JPY": 155.50
                }
            }
        """;
        Map<String, BigDecimal> v6Rates = HttpExchangeRateProvider.parseRatesFromJson(v6Format);
        assertEquals(3, v6Rates.size());
        assertEquals(new BigDecimal("0.78"), v6Rates.get("GBP"));
        assertEquals(new BigDecimal("155.50"), v6Rates.get("JPY"));

        // Malformed / Empty
        assertTrue(HttpExchangeRateProvider.parseRatesFromJson(null).isEmpty());
        assertTrue(HttpExchangeRateProvider.parseRatesFromJson("{}").isEmpty());
    }

    @Test
    @DisplayName("Audit 6: Unavailable exchange rate fails fast and never assumes 1.0")
    void testUnavailableRateFailsFast() {
        String dbPath = tempDir.resolve("fail_fast_test.db").toAbsolutePath().toString();
        try (SQLiteStorageEngine engine = new SQLiteStorageEngine(dbPath)) {
            MockExchangeRateProvider emptyProvider = new MockExchangeRateProvider();
            emptyProvider.clear();
            CurrencyConversionService service = new CurrencyConversionService(emptyProvider, engine);

            assertThrows(CurrencyConversionException.class, () ->
                    service.convert(new BigDecimal("500.00"), "USD", "XYZ")
            );
        }
    }

    @Test
    @DisplayName("Audit 7: Cross-rate triangulation via USD and inverse derivation precision")
    void testTriangulationAndInverseDerivationPrecision() {
        String dbPath = tempDir.resolve("triangulation_test.db").toAbsolutePath().toString();
        try (SQLiteStorageEngine engine = new SQLiteStorageEngine(dbPath)) {
            // Direct quotations: USD->EUR = 0.90, USD->INR = 81.00
            engine.saveExchangeRate(new ExchangeRate("USD", "EUR", new BigDecimal("0.900000000000"), LocalDateTime.now(), "TestSource"));
            engine.saveExchangeRate(new ExchangeRate("USD", "INR", new BigDecimal("81.000000000000"), LocalDateTime.now(), "TestSource"));

            MockExchangeRateProvider emptyProvider = new MockExchangeRateProvider();
            emptyProvider.clear();
            CurrencyConversionService service = new CurrencyConversionService(emptyProvider, engine);

            // EUR -> INR via USD triangulation:
            // EUR -> USD = 1 / 0.90 = 1.111111111111
            // USD -> INR = 81.00
            // Cross rate = 1.111111111111 * 81.00 = 90.00
            // 200 EUR = 18000.00 INR
            BigDecimal inr = service.convert(new BigDecimal("200.00"), "EUR", "INR");
            assertEquals(new BigDecimal("18000.00"), inr);

            // INR -> EUR via USD triangulation:
            // 18000.00 INR -> EUR should yield 200.00 EUR
            BigDecimal eur = service.convert(new BigDecimal("18000.00"), "INR", "EUR");
            assertEquals(new BigDecimal("200.00"), eur);
        }
    }
}
