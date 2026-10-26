package com.fintrack;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
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
import com.fintrack.service.dto.ForecastResult;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.service.dto.VelocityMetrics;
import com.fintrack.storage.CsvStorageEngine;

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
}
