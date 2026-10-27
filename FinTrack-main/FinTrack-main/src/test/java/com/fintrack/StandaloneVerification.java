package com.fintrack;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.ExchangeRate;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.model.ValidationException;
import com.fintrack.service.AnalyticsProcessor;
import com.fintrack.service.BudgetManager;
import com.fintrack.service.BudgetStatus;
import com.fintrack.service.CategoryBudgetReport;
import com.fintrack.service.CurrencyConversionException;
import com.fintrack.service.CurrencyConversionService;
import com.fintrack.service.MockExchangeRateProvider;
import com.fintrack.service.dto.ForecastResult;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.service.dto.VelocityMetrics;
import com.fintrack.storage.CsvStorageEngine;
import com.fintrack.storage.SQLiteStorageEngine;
import com.fintrack.util.CsvUtil;
import com.fintrack.util.CurrencyFormatter;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Standalone console test harness verifying core invariants, validations,
 * and data persistence without requiring a JUnit CLI runner.
 */
public class StandaloneVerification {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("            FinTrack Infrastructure Verification Suite             ");
        System.out.println("==================================================================\n");

        runTest("Model Validation: Legitimate Transaction Creation", StandaloneVerification::testValidTransaction);
        runTest("Model Validation: Invariant Rejection (Amount <= 0)", StandaloneVerification::testNegativeAmount);
        runTest("Model Validation: Email Syntax Guard", StandaloneVerification::testUserEmailValidation);
        runTest("Model Validation: Goal Progress Calculation", StandaloneVerification::testGoalProgress);
        runTest("Storage Engine: CSV Flat-File Persistence & Reload", StandaloneVerification::testCsvStorage);
        runTest("Budget Manager: Limits, Spending & Threshold Statuses", StandaloneVerification::testBudgetManager);
        runTest("Analytics Processor: Velocity, Forecast & Health Summary", StandaloneVerification::testAnalyticsProcessor);
        runTest("Seed Data Generator: 3-Month Realistic INR Ledger", StandaloneVerification::testSeedData);
        runTest("Currency Formatter: Symbol Mapping, Formatting & Numeric Sorting", StandaloneVerification::testCurrencyFormatter);
        runTest("SEC-01 & DATA-02: CSV Formula Injection Mitigation & Multiline Round-Trip", StandaloneVerification::testCsvFormulaInjectionAndMultiline);
        runTest("DATA-01: Goal Contribution Accounting & Asset Conservation", StandaloneVerification::testGoalContributionAccounting);
        runTest("DATA-03: SQLite Runtime Schema CHECK Constraints Enforcement", StandaloneVerification::testSqliteSchemaConstraints);
        runTest("FUNC-01: Dashboard Income Calculation & Run-Rate Stabilization", StandaloneVerification::testDashboardIncomeCalculation);
        runTest("CURR-01: Real Currency Conversion, Precision & Offline Fallback", StandaloneVerification::testCurrencyConversionPrecisionAndOffline);
        runTest("CURR-02: Multi-Currency Accounting Integrity & Preservation", StandaloneVerification::testMultiCurrencyAccountingInvariants);

        System.out.println("\n------------------------------------------------------------------");
        System.out.printf("Test Execution Complete: %d Passed | %d Failed\n", testsPassed, testsFailed);
        System.out.println("------------------------------------------------------------------");
    }

    private static void runTest(String testName, Runnable testLogic) {
        try {
            testLogic.run();
            System.out.printf("[PASS] %s\n", testName);
            testsPassed++;
        } catch (Throwable t) {
            System.out.printf("[FAIL] %s -> %s\n", testName, t.getMessage());
            testsFailed++;
        }
    }

    private static void testValidTransaction() {
        Transaction tx = new Transaction(
                Category.FOOD_AND_DINING,
                new BigDecimal("42.80"),
                PaymentMode.CARD,
                TransactionType.EXPENSE,
                "Bistro lunch"
        );
        if (tx.getId() == null || tx.getId().isBlank()) throw new AssertionError("ID must not be empty");
        if (tx.getCategory() != Category.FOOD_AND_DINING) throw new AssertionError("Category mismatch");
        if (tx.getAmount().compareTo(new BigDecimal("42.80")) != 0) throw new AssertionError("Amount mismatch");
    }

    private static void testNegativeAmount() {
        try {
            new Transaction(
                    Category.GROCERIES,
                    new BigDecimal("-10.00"),
                    PaymentMode.CASH,
                    TransactionType.EXPENSE,
                    "Invalid"
            );
            throw new AssertionError("Expected ValidationException for negative amount");
        } catch (ValidationException expected) {
            // Success
        }
    }

    private static void testUserEmailValidation() {
        try {
            new User("bad-id", "User", "not_an_email", "USD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            throw new AssertionError("Expected ValidationException for invalid email");
        } catch (ValidationException expected) {
            // Success
        }
    }

    private static void testGoalProgress() {
        Goal goal = new Goal("g1", "u1", "Vacation", new BigDecimal("5000.00"), new BigDecimal("1250.00"), LocalDate.now().plusMonths(3));
        if (Math.abs(goal.getProgressPercentage() - 25.0) > 0.001) {
            throw new AssertionError("Progress percentage calculation failed. Expected 25.0, got " + goal.getProgressPercentage());
        }
    }

    private static void testCsvStorage() {
        try {
            Path tempDir = Files.createTempDirectory("fintrack_verify_");
            try (CsvStorageEngine engine = new CsvStorageEngine(tempDir.toString())) {
                User user = new User("u-test", "Alice", "alice@example.com", "USD",
                        new BigDecimal("7000.00"), new BigDecimal("3000.00"), new BigDecimal("1500.00"));
                engine.saveUser(user);

                Transaction tx = new Transaction("tx-test", "u-test", LocalDateTime.now(),
                        Category.UTILITIES, new BigDecimal("150.00"), PaymentMode.BANK_TRANSFER,
                        TransactionType.EXPENSE, "Electric bill");
                engine.saveTransaction(tx);
            }

            // Verify disk recovery
            try (CsvStorageEngine reloadEngine = new CsvStorageEngine(tempDir.toString())) {
                User reloaded = reloadEngine.getUserById("u-test").orElseThrow(() -> new AssertionError("User not recovered from CSV"));
                if (!"Alice".equals(reloaded.getName())) throw new AssertionError("User name corrupted");

                Transaction reloadedTx = reloadEngine.getTransactionById("tx-test").orElseThrow(() -> new AssertionError("Transaction not recovered from CSV"));
                if (reloadedTx.getAmount().compareTo(new BigDecimal("150.00")) != 0) throw new AssertionError("Transaction amount corrupted");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void testBudgetManager() {
        try {
            Path tempDir = Files.createTempDirectory("fintrack_bm_verify_");
            try (CsvStorageEngine engine = new CsvStorageEngine(tempDir.toString())) {
                BudgetManager bm = new BudgetManager(engine);
                String uid = "bm-user";

                // Set budget: 1000 for ENTERTAINMENT
                bm.setCategoryBudget(uid, Category.ENTERTAINMENT, new BigDecimal("1000.00"));

                // 1. Initial status: 0 spent -> OK (< 75%)
                if (bm.getBudgetStatus(uid, Category.ENTERTAINMENT) != BudgetStatus.OK) {
                    throw new AssertionError("Initial status should be OK");
                }

                // 2. Spend 800 (80%) -> WARNING (75% - 100%)
                engine.saveTransaction(new Transaction(Category.ENTERTAINMENT, new BigDecimal("800.00"),
                        PaymentMode.CARD, TransactionType.EXPENSE, "Concert tickets"));
                // Need to update transaction's userId
                Transaction tx1 = engine.getAllTransactions().get(0);
                tx1.setUserId(uid);
                engine.updateTransaction(tx1);

                if (bm.getBudgetStatus(uid, Category.ENTERTAINMENT) != BudgetStatus.WARNING) {
                    throw new AssertionError("80% spent should be WARNING status");
                }

                // 3. Spend additional 300 (Total 1100 = 110%) -> EXCEEDED (> 100%)
                Transaction tx2 = new Transaction("tx-over", uid, LocalDateTime.now(), Category.ENTERTAINMENT,
                        new BigDecimal("300.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Party");
                engine.saveTransaction(tx2);

                if (bm.getBudgetStatus(uid, Category.ENTERTAINMENT) != BudgetStatus.EXCEEDED) {
                    throw new AssertionError("110% spent should be EXCEEDED status");
                }

                // 4. Milestone goal contribution
                Goal goal = bm.createGoal(uid, "MacBook Pro", new BigDecimal("2000.00"), LocalDate.now().plusMonths(4));
                bm.contributeToGoal(goal.getId(), new BigDecimal("1000.00"));
                if (Math.abs(bm.getGoalProgress(goal.getId()) - 50.0) > 0.001) {
                    throw new AssertionError("Goal progress should be 50.0%");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void testAnalyticsProcessor() {
        try {
            Path tempDir = Files.createTempDirectory("fintrack_ap_verify_");
            try (CsvStorageEngine engine = new CsvStorageEngine(tempDir.toString())) {
                BudgetManager bm = new BudgetManager(engine);
                AnalyticsProcessor ap = new AnalyticsProcessor(engine, bm);
                String uid = "ap-user";

                User user = new User(uid, "Test Analyst", "analyst@test.com", "USD",
                        new BigDecimal("5000.00"), new BigDecimal("2500.00"), new BigDecimal("1000.00"));
                engine.saveUser(user);

                // Add salary income: 5000
                engine.saveTransaction(new Transaction("tx-in", uid, LocalDateTime.now().minusDays(3),
                        Category.SALARY, new BigDecimal("5000.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Salary"));

                // Add expense: 1500
                engine.saveTransaction(new Transaction("tx-out", uid, LocalDateTime.now().minusDays(1),
                        Category.HOUSING, new BigDecimal("1500.00"), PaymentMode.BANK_TRANSFER, TransactionType.EXPENSE, "Rent"));

                // 1. Velocity test
                VelocityMetrics vm = ap.computeVelocity(uid, 10);
                if (vm.totalTransactions() != 2) throw new AssertionError("Expected 2 transactions");
                if (vm.totalSpend().compareTo(new BigDecimal("1500.00")) != 0) throw new AssertionError("Spend mismatch");

                // 2. Forecast test
                ForecastResult fr = ap.computeBalanceForecast(uid, 3);
                if (fr.currentBalance().compareTo(new BigDecimal("3500.00")) != 0) throw new AssertionError("Current balance mismatch");
                if (fr.futureForecasts().size() != 3) throw new AssertionError("Expected 3 future forecasts");

                // 3. Health summary test
                HealthSummary hs = ap.computeHealthSummary(uid);
                if (hs.savingsRate() <= 0) throw new AssertionError("Savings rate should be positive");
                if (hs.budgetComplianceScore() < 50) throw new AssertionError("Health score should be healthy");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void testSeedData() {
        try {
            Path tempDir = Files.createTempDirectory("fintrack_seed_verify_");
            try (CsvStorageEngine engine = new CsvStorageEngine(tempDir.toString())) {
                com.fintrack.util.SeedData.seed(engine, false);

                // Verify user created
                User user = engine.getUserById(com.fintrack.util.SeedData.DEFAULT_USER_ID)
                        .orElseThrow(() -> new AssertionError("Seed user not created"));
                if (!"INR".equals(user.getCurrency())) throw new AssertionError("Currency should be INR");

                // Verify transactions seeded across 3 months
                List<Transaction> txList = engine.getTransactionsByUserId(user.getId());
                if (txList.size() < 15) throw new AssertionError("Expected at least 15 seeded transactions, got " + txList.size());

                // Verify budgets seeded
                List<Budget> budgets = engine.getBudgetsByUserId(user.getId());
                if (budgets.size() < 10) throw new AssertionError("Expected budgets across quarters, got " + budgets.size());

                // Verify goals seeded
                List<Goal> goals = engine.getGoalsByUserId(user.getId());
                if (goals.size() != 3) throw new AssertionError("Expected 3 milestone goals, got " + goals.size());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void testCurrencyFormatter() {
        if (!"₹".equals(CurrencyFormatter.getCurrencySymbol("INR"))) throw new AssertionError("INR symbol mismatch");
        if (!"$".equals(CurrencyFormatter.getCurrencySymbol("USD"))) throw new AssertionError("USD symbol mismatch");
        if (!"€".equals(CurrencyFormatter.getCurrencySymbol("EUR"))) throw new AssertionError("EUR symbol mismatch");
        if (!"£".equals(CurrencyFormatter.getCurrencySymbol("GBP"))) throw new AssertionError("GBP symbol mismatch");

        if (!"₹1250.50".equals(CurrencyFormatter.format(new BigDecimal("1250.5"), "INR"))) {
            throw new AssertionError("Format mismatch for INR");
        }
        if (!"-₹500.00".equals(CurrencyFormatter.formatTransactionAmount(new BigDecimal("500"), TransactionType.EXPENSE, "INR"))) {
            throw new AssertionError("Expense format mismatch");
        }
        if (!"+₹500.00".equals(CurrencyFormatter.formatTransactionAmount(new BigDecimal("500"), TransactionType.INCOME, "INR"))) {
            throw new AssertionError("Income format mismatch");
        }
        if (CurrencyFormatter.compareFormattedAmounts("+₹1000.00", "+₹200.00") <= 0) {
            throw new AssertionError("Sorting comparator should rank +₹1000 > +₹200");
        }
        if (CurrencyFormatter.compareFormattedAmounts("+₹50.00", "-₹100.00") <= 0) {
            throw new AssertionError("Sorting comparator should rank +₹50 > -₹100");
        }
        if (CurrencyFormatter.compareFormattedAmounts("-₹200.00", "-₹50.00") >= 0) {
            throw new AssertionError("Sorting comparator should rank -₹200 < -₹50");
        }
    }

    private static void testCsvFormulaInjectionAndMultiline() {
        // SEC-01: Formula Injection Sanitization
        String dangerousFormula = "=cmd|' /C calc'!A0";
        String escaped = CsvUtil.escapeField(dangerousFormula);
        if (!escaped.startsWith("'=")) {
            throw new AssertionError("Formula starting with '=' must be escaped with single quote. Got: " + escaped);
        }
        if (!CsvUtil.escapeField("+1234").startsWith("'+")) throw new AssertionError("+ prefix not sanitized");
        if (!CsvUtil.escapeField("-5678").startsWith("'-")) throw new AssertionError("- prefix not sanitized");
        if (!CsvUtil.escapeField("@SUM(A1)").startsWith("'@")) throw new AssertionError("@ prefix not sanitized");
        if (!CsvUtil.escapeField("\tTAB").startsWith("'\t")) throw new AssertionError("\\t prefix not sanitized");
        if (!CsvUtil.escapeField("\rRETURN").startsWith("'\r")) throw new AssertionError("\\r prefix not sanitized");

        // Safe strings should not be prefixed
        if (!"Normal Description".equals(CsvUtil.escapeField("Normal Description"))) {
            throw new AssertionError("Normal string should not be prefixed");
        }

        // Unescaping restores the original string
        String unescaped = CsvUtil.unescapeField(escaped);
        if (!dangerousFormula.equals(unescaped)) {
            throw new AssertionError("Unescape failed. Expected: " + dangerousFormula + ", Got: " + unescaped);
        }

        // DATA-02: Multiline CSV Notes Round-Trip
        try {
            Path tempDir = Files.createTempDirectory("fintrack_csv_multiline_");
            String multilineNote = "Line 1: Invoice Received\nLine 2: \"Verified by Accountant\"\nLine 3: Paid in Full";
            String formulaNote = "=SUM(B1:B10)";

            try (CsvStorageEngine engine = new CsvStorageEngine(tempDir.toString())) {
                User u = new User("u-csv", "CSV User", "csv@test.com", "USD", new BigDecimal("5000.00"), new BigDecimal("2000.00"), new BigDecimal("1000.00"));
                engine.saveUser(u);

                Transaction t1 = new Transaction("tx-multi", "u-csv", LocalDateTime.now().minusHours(2),
                        Category.SHOPPING, new BigDecimal("299.99"), PaymentMode.CARD, TransactionType.EXPENSE, multilineNote);
                engine.saveTransaction(t1);

                Transaction t2 = new Transaction("tx-formula", "u-csv", LocalDateTime.now().minusHours(1),
                        Category.OTHER_INCOME, new BigDecimal("50.00"), PaymentMode.CASH, TransactionType.INCOME, formulaNote);
                engine.saveTransaction(t2);
            }

            // Reload from disk and verify notes were not mangled or split
            try (CsvStorageEngine reloadEngine = new CsvStorageEngine(tempDir.toString())) {
                Transaction reloaded1 = reloadEngine.getTransactionById("tx-multi")
                        .orElseThrow(() -> new AssertionError("Multiline transaction not found after reload"));
                if (!multilineNote.equals(reloaded1.getNote())) {
                    throw new AssertionError("Multiline note corrupted. Expected:\n" + multilineNote + "\nGot:\n" + reloaded1.getNote());
                }

                Transaction reloaded2 = reloadEngine.getTransactionById("tx-formula")
                        .orElseThrow(() -> new AssertionError("Formula transaction not found after reload"));
                if (!formulaNote.equals(reloaded2.getNote())) {
                    throw new AssertionError("Formula note corrupted. Expected: " + formulaNote + ", Got: " + reloaded2.getNote());
                }
            }

            // Verify with SQLiteStorageEngine CSV import / export
            Path sqliteExportDir = Files.createTempDirectory("fintrack_sqlite_csv_");
            try (SQLiteStorageEngine sqlite = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
                sqlite.initialize();
                User u = new User("u-csv", "CSV User", "csv@test.com", "USD", new BigDecimal("5000.00"), new BigDecimal("2000.00"), new BigDecimal("1000.00"));
                sqlite.saveUser(u);

                Transaction t1 = new Transaction("tx-multi", "u-csv", LocalDateTime.now().minusHours(2),
                        Category.SHOPPING, new BigDecimal("299.99"), PaymentMode.CARD, TransactionType.EXPENSE, multilineNote);
                sqlite.saveTransaction(t1);

                sqlite.exportToCsv(sqliteExportDir.toString());
            }

            // Import into a fresh SQLite database and verify
            try (SQLiteStorageEngine sqliteFresh = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
                sqliteFresh.initialize();
                sqliteFresh.importFromCsv(sqliteExportDir.toString());

                Transaction reloaded1 = sqliteFresh.getTransactionById("tx-multi")
                        .orElseThrow(() -> new AssertionError("SQLite imported multiline transaction not found"));
                if (!multilineNote.equals(reloaded1.getNote())) {
                    throw new AssertionError("SQLite multiline note corrupted on CSV import");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV Security & Multiline verification failed: " + e.getMessage(), e);
        }
    }

    private static void testGoalContributionAccounting() {
        try (SQLiteStorageEngine storage = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
            storage.initialize();
            BudgetManager bm = new BudgetManager(storage);
            AnalyticsProcessor ap = new AnalyticsProcessor(storage, bm);
            String uid = "user-goal-acc";

            User user = new User(uid, "Goal Saver", "saver@test.com", "USD",
                    new BigDecimal("6000.00"), new BigDecimal("2500.00"), new BigDecimal("1000.00"));
            storage.saveUser(user);

            // User has $5,000 income
            Transaction incomeTx = new Transaction("tx-inc-goal", uid, LocalDateTime.now().minusDays(5),
                    Category.SALARY, new BigDecimal("5000.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Monthly Salary");
            storage.saveTransaction(incomeTx);

            BigDecimal initialBalance = ap.computeCurrentBalance(uid);
            if (initialBalance.compareTo(new BigDecimal("5000.00")) != 0) {
                throw new AssertionError("Initial balance should be 5000.00, got: " + initialBalance);
            }

            // Create goal of $2,000
            Goal goal = bm.createGoal(uid, "Emergency Fund", new BigDecimal("2000.00"), LocalDate.now().plusMonths(6));

            // Contribute $1,200 to goal
            Goal updatedGoal = bm.contributeToGoal(goal.getId(), new BigDecimal("1200.00"), PaymentMode.BANK_TRANSFER);
            if (updatedGoal.getCurrentAmount().compareTo(new BigDecimal("1200.00")) != 0) {
                throw new AssertionError("Goal current amount should be 1200.00");
            }

            // Check cash balance: should now be 5000 - 1200 = 3800
            BigDecimal balanceAfter = ap.computeCurrentBalance(uid);
            if (balanceAfter.compareTo(new BigDecimal("3800.00")) != 0) {
                throw new AssertionError("Cash balance after contribution should be 3800.00 (decreased), got: " + balanceAfter);
            }

            // Total net worth must be conserved: 3800 (cash) + 1200 (goal) = 5000.00
            BigDecimal totalNetWorth = balanceAfter.add(updatedGoal.getCurrentAmount());
            if (totalNetWorth.compareTo(new BigDecimal("5000.00")) != 0) {
                throw new AssertionError("Net worth not conserved! Expected 5000.00, got: " + totalNetWorth);
            }

            // Check that a Category.SAVINGS expense transaction was recorded
            List<Transaction> userTxs = storage.getTransactionsByUserId(uid);
            boolean foundSavingsTx = userTxs.stream().anyMatch(t ->
                    t.getCategory() == Category.SAVINGS
                    && t.getType() == TransactionType.EXPENSE
                    && t.getAmount().compareTo(new BigDecimal("1200.00")) == 0
                    && t.getNote().contains("Emergency Fund"));

            if (!foundSavingsTx) {
                throw new AssertionError("Category.SAVINGS expense transaction for goal contribution was not recorded in ledger!");
            }
        }
    }

    private static void testSqliteSchemaConstraints() {
        try (SQLiteStorageEngine storage = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
            storage.initialize();

            // 1. users CHECK (monthly_income >= 0)
            try {
                storage.saveUser(new User("u-neg-inc", "Neg User", "neg@test.com", "USD",
                        new BigDecimal("-100.00"), new BigDecimal("500.00"), new BigDecimal("100.00")));
                throw new AssertionError("Should reject user with negative monthly_income");
            } catch (Exception expected) {
                // Expected constraint violation
            }

            // Save valid user
            storage.saveUser(new User("u-valid", "Valid User", "valid@test.com", "USD",
                    new BigDecimal("5000.00"), new BigDecimal("2000.00"), new BigDecimal("1000.00")));

            // 2. transactions table CHECK constraints via direct SQL against the initialized engine
            try {
                java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
                try (java.sql.Statement s = conn.createStatement()) {
                    s.execute("PRAGMA foreign_keys = ON;");
                    s.execute("""
                        CREATE TABLE transactions (
                            id TEXT PRIMARY KEY,
                            user_id TEXT NOT NULL,
                            timestamp TEXT NOT NULL,
                            category TEXT NOT NULL,
                            amount NUMERIC NOT NULL CHECK (amount > 0),
                            payment_mode TEXT NOT NULL,
                            type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
                            note TEXT
                        );
                    """);
                    try {
                        s.execute("INSERT INTO transactions VALUES ('t1','u1','2026-10-26T12:00:00','FOOD',-50.0,'CASH','EXPENSE','note');");
                        throw new AssertionError("DB allowed negative amount in transactions table");
                    } catch (java.sql.SQLException expected) {
                        // Success: CHECK constraint failed: amount > 0
                    }

                    try {
                        s.execute("INSERT INTO transactions VALUES ('t2','u1','2026-10-26T12:00:00','FOOD',50.0,'CASH','INVALID_TYPE','note');");
                        throw new AssertionError("DB allowed invalid transaction type");
                    } catch (java.sql.SQLException expected) {
                        // Success: CHECK constraint failed: type IN ('INCOME', 'EXPENSE')
                    }
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }

            // 3. budgets CHECK constraints (month between 1 and 12)
            try {
                Budget invalidBudget = new Budget("b-inv", "u-valid", Category.FOOD_AND_DINING, new BigDecimal("500.00"), 13, 2026);
                storage.saveBudget(invalidBudget);
                throw new AssertionError("DB allowed budget with month = 13");
            } catch (Exception expected) {
                // Expected constraint violation
            }

            // 4. goals CHECK constraints (target_amount > 0)
            try {
                Goal invalidGoal = new Goal("g-inv", "u-valid", "Invalid Goal", new BigDecimal("-100.00"), BigDecimal.ZERO, LocalDate.now().plusMonths(1));
                storage.saveGoal(invalidGoal);
                throw new AssertionError("DB allowed goal with negative target_amount");
            } catch (Exception expected) {
                // Expected constraint violation
            }
        }
    }

    private static void testDashboardIncomeCalculation() {
        try (SQLiteStorageEngine storage = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
            storage.initialize();
            BudgetManager bm = new BudgetManager(storage);
            AnalyticsProcessor ap = new AnalyticsProcessor(storage, bm);
            String uid = "user-dash-inc";

            // Expected monthly salary = 6000.00
            User user = new User(uid, "Salary Earner", "salary@test.com", "USD",
                    new BigDecimal("6000.00"), new BigDecimal("3000.00"), new BigDecimal("1500.00"));
            storage.saveUser(user);

            // Phase 1: Zero transactions in the month -> effective income should be 6000.00
            HealthSummary hsInitial = ap.computeHealthSummary(uid);
            if (hsInitial.totalIncome().compareTo(new BigDecimal("6000.00")) != 0) {
                throw new AssertionError("Initial effective income should be 6000.00, got: " + hsInitial.totalIncome());
            }

            // Phase 2: Add a minor income transaction (e.g. $10 cashback or interest)
            Transaction smallIncome = new Transaction("tx-small", uid, LocalDateTime.now(),
                    Category.OTHER_INCOME, new BigDecimal("10.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Cashback");
            storage.saveTransaction(smallIncome);

            // FUNC-01 verification: Effective income must NOT collapse from 6000.00 to 10.00!
            HealthSummary hsAfterSmall = ap.computeHealthSummary(uid);
            if (hsAfterSmall.totalIncome().compareTo(new BigDecimal("6000.00")) != 0) {
                throw new AssertionError("FUNC-01 regression! Effective income collapsed to: " + hsAfterSmall.totalIncome() + " instead of expected baseline 6000.00");
            }

            // Phase 3: Add actual salary that exceeds baseline (e.g. $6500.00 salary with bonus)
            Transaction largeSalary = new Transaction("tx-large", uid, LocalDateTime.now(),
                    Category.SALARY, new BigDecimal("6500.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Salary + Bonus");
            storage.saveTransaction(largeSalary);

            // Total actual income is now 10 + 6500 = 6510.00. Effective income should be 6510.00
            HealthSummary hsAfterSalary = ap.computeHealthSummary(uid);
            if (hsAfterSalary.totalIncome().compareTo(new BigDecimal("6510.00")) != 0) {
                throw new AssertionError("Effective income should reflect actual higher income 6510.00, got: " + hsAfterSalary.totalIncome());
            }
        }
    }

    private static void testCurrencyConversionPrecisionAndOffline() {
        try (SQLiteStorageEngine storage = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
            storage.initialize();

            MockExchangeRateProvider mock = new MockExchangeRateProvider();
            mock.setExchangeRate("USD", "INR", new BigDecimal("83.250000"));
            mock.setExchangeRate("USD", "EUR", new BigDecimal("0.920000"));
            mock.setExchangeRate("USD", "JPY", new BigDecimal("155.500000"));

            CurrencyConversionService service = new CurrencyConversionService(mock, storage);

            // 1. Standard conversion
            BigDecimal inr = service.convert(new BigDecimal("100.00"), "USD", "INR");
            if (inr.compareTo(new BigDecimal("8325.00")) != 0) {
                throw new AssertionError("100 USD to INR expected 8325.00, got: " + inr);
            }

            // 2. JPY 0 fraction digits precision
            BigDecimal jpy = service.convert(new BigDecimal("10.50"), "USD", "JPY");
            if (jpy.compareTo(new BigDecimal("1633")) != 0 || jpy.scale() != 0) {
                throw new AssertionError("10.50 USD to JPY expected 1633 with scale 0, got: " + jpy + " (scale=" + jpy.scale() + ")");
            }

            // 3. Persistent SQLite caching & Offline fallback
            if (storage.getExchangeRate("USD", "INR").isEmpty()) {
                throw new AssertionError("Exchange rate USD->INR was not persisted into SQLite cache table");
            }

            // Simulate network outage
            mock.clear();
            CurrencyConversionService offlineService = new CurrencyConversionService(mock, storage);
            BigDecimal offlineInr = offlineService.convert(new BigDecimal("10.00"), "USD", "INR");
            if (offlineInr.compareTo(new BigDecimal("832.50")) != 0) {
                throw new AssertionError("Offline conversion failed. Expected 832.50, got: " + offlineInr);
            }

            // 4. Missing rate must throw CurrencyConversionException, never assume 1.0
            try {
                offlineService.convert(new BigDecimal("100.00"), "USD", "KRW");
                throw new AssertionError("Expected CurrencyConversionException for unresolvable currency pair");
            } catch (CurrencyConversionException expected) {
                // Correctly rejected
            }
        }
    }

    private static void testMultiCurrencyAccountingInvariants() {
        try (SQLiteStorageEngine storage = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
            storage.initialize();

            MockExchangeRateProvider mock = new MockExchangeRateProvider();
            mock.clear();
            mock.setExchangeRate("USD", "INR", new BigDecimal("80.000000"));
            mock.setExchangeRate("USD", "EUR", new BigDecimal("0.800000"));

            CurrencyConversionService service = new CurrencyConversionService(mock, storage);
            BudgetManager bm = new BudgetManager(storage, service);
            AnalyticsProcessor ap = new AnalyticsProcessor(storage, bm, service);

            // User with base currency USD and reporting currency USD
            User user = new User("u-curr", "Expat User", "expat@test.com", "USD",
                    new BigDecimal("5000.00"), new BigDecimal("2000.00"), new BigDecimal("1000.00"));
            user.setReportingCurrency("USD");
            storage.saveUser(user);

            // Original transaction 1: 800.00 INR Expense (at 80.00 INR/USD = 10.00 USD)
            Transaction tx1 = new Transaction("tx-inr", user.getId(), LocalDateTime.now(),
                    Category.FOOD_AND_DINING, new BigDecimal("800.00"), "INR",
                    PaymentMode.CARD, TransactionType.EXPENSE, "Delhi Street Food");
            storage.saveTransaction(tx1);

            // Original transaction 2: 80.00 EUR Expense (at 0.80 EUR/USD = 100.00 USD)
            Transaction tx2 = new Transaction("tx-eur", user.getId(), LocalDateTime.now(),
                    Category.ENTERTAINMENT, new BigDecimal("80.00"), "EUR",
                    PaymentMode.CARD, TransactionType.EXPENSE, "Paris Concert");
            storage.saveTransaction(tx2);

            // Verify original values and currencies are preserved without tampering
            Transaction retrieved1 = storage.getTransactionById("tx-inr").orElseThrow();
            if (!"INR".equals(retrieved1.getCurrency()) || retrieved1.getAmount().compareTo(new BigDecimal("800.00")) != 0) {
                throw new AssertionError("Original transaction currency or amount altered in storage!");
            }

            Transaction retrieved2 = storage.getTransactionById("tx-eur").orElseThrow();
            if (!"EUR".equals(retrieved2.getCurrency()) || retrieved2.getAmount().compareTo(new BigDecimal("80.00")) != 0) {
                throw new AssertionError("Original transaction currency or amount altered in storage!");
            }

            // Verify converted aggregation: Total expense in USD = 10.00 + 100.00 = 110.00 USD
            BigDecimal balance = ap.computeCurrentBalance(user.getId());
            if (balance.compareTo(new BigDecimal("-110.00")) != 0) {
                throw new AssertionError("Converted balance aggregation mismatch! Expected -110.00, got: " + balance);
            }

            // Test switching reporting currency dynamically to INR
            user.setReportingCurrency("INR");
            storage.updateUser(user);

            // In INR: -110.00 USD * 80 = -8800.00 INR
            BigDecimal balanceInInr = ap.computeCurrentBalance(user.getId());
            if (balanceInInr.compareTo(new BigDecimal("-8800.00")) != 0) {
                throw new AssertionError("Dynamically converted balance in INR mismatch! Expected -8800.00, got: " + balanceInInr);
            }
        }
    }
}
