package com.fintrack.service;

import com.fintrack.model.Category;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.service.dto.CategoryRunRate;
import com.fintrack.service.dto.CategorySpendingShare;
import com.fintrack.service.dto.ForecastResult;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.service.dto.VelocityMetrics;
import com.fintrack.storage.SQLiteStorageEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalyticsProcessor Computational Tests")
public class AnalyticsProcessorTest {

    private SQLiteStorageEngine storage;
    private BudgetManager budgetManager;
    private AnalyticsProcessor processor;
    private final String userId = "user-analyst-1";

    @BeforeEach
    void setUp() {
        storage = new SQLiteStorageEngine("jdbc:sqlite::memory:");
        budgetManager = new BudgetManager(storage);
        processor = new AnalyticsProcessor(storage, budgetManager);

        User user = new User(userId, "Dana Scully", "dana@fintrack.org", "USD",
                new BigDecimal("8000.00"), new BigDecimal("3500.00"), new BigDecimal("2000.00"));
        storage.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("Should compute transaction velocity over past window")
    void testVelocityComputation() {
        LocalDateTime now = LocalDateTime.now();

        // 3 transactions over the last 10 days
        storage.saveTransaction(new Transaction("tx-v1", userId, now.minusDays(2), Category.GROCERIES,
                new BigDecimal("100.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Groceries"));
        storage.saveTransaction(new Transaction("tx-v2", userId, now.minusDays(5), Category.FOOD_AND_DINING,
                new BigDecimal("50.00"), PaymentMode.UPI, TransactionType.EXPENSE, "Lunch"));
        storage.saveTransaction(new Transaction("tx-v3", userId, now.minusDays(8), Category.TRANSPORTATION,
                new BigDecimal("50.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Fuel"));

        // Analyze 10-day window
        VelocityMetrics velocity = processor.computeVelocity(userId, 10);
        assertEquals(3, velocity.totalTransactions());
        assertEquals(0, new BigDecimal("200.00").compareTo(velocity.totalSpend()));
        assertEquals(0.3, velocity.dailyTransactionCount(), 0.001);
        assertEquals(0, new BigDecimal("20.00").compareTo(velocity.dailySpend()));
        assertEquals(2.1, velocity.weeklyTransactionCount(), 0.001);
        assertEquals(0, new BigDecimal("140.00").compareTo(velocity.weeklySpend()));
    }

    @Test
    @DisplayName("Should compute category run-rate and month-over-month variance")
    void testCategoryRunRateAndMom() {
        YearMonth nowYm = YearMonth.now();
        YearMonth prevYm = nowYm.minusMonths(1);

        // Previous month spend: 300 in GROCERIES
        storage.saveTransaction(new Transaction("tx-p1", userId, prevYm.atDay(10).atTime(12, 0),
                Category.GROCERIES, new BigDecimal("300.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Last month groceries"));

        // Current month spend: 200 in GROCERIES
        storage.saveTransaction(new Transaction("tx-c1", userId, nowYm.atDay(1).atTime(12, 0),
                Category.GROCERIES, new BigDecimal("200.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Current month groceries"));

        List<CategoryRunRate> runRates = processor.computeCategoryRunRates(userId, nowYm.getMonthValue(), nowYm.getYear());
        assertFalse(runRates.isEmpty());

        CategoryRunRate groceryRunRate = runRates.stream()
                .filter(r -> r.category() == Category.GROCERIES)
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("200.00").compareTo(groceryRunRate.currentSpent()));
        assertEquals(0, new BigDecimal("300.00").compareTo(groceryRunRate.previousMonthSpent()));
        assertNotNull(groceryRunRate.projectedMonthlySpend());
        assertNotNull(groceryRunRate.dailyRunRate());
    }

    @Test
    @DisplayName("Should project linear rest-of-month and forward 3-month balances")
    void testBalanceForecast() {
        YearMonth nowYm = YearMonth.now();

        // Income: 8000
        storage.saveTransaction(new Transaction("tx-in", userId, nowYm.atDay(1).atTime(9, 0),
                Category.SALARY, new BigDecimal("8000.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Salary"));

        // Expense: 2000
        storage.saveTransaction(new Transaction("tx-out", userId, nowYm.atDay(2).atTime(14, 0),
                Category.HOUSING, new BigDecimal("2000.00"), PaymentMode.BANK_TRANSFER, TransactionType.EXPENSE, "Rent"));

        ForecastResult forecast = processor.computeBalanceForecast(userId, 3);

        // Current balance = 8000 - 2000 = 6000
        assertEquals(0, new BigDecimal("6000.00").compareTo(forecast.currentBalance()));
        assertNotNull(forecast.projectedCurrentMonthEndBalance());

        // 3 forward monthly projections
        assertEquals(3, forecast.futureForecasts().size());
        assertEquals(nowYm.plusMonths(1), forecast.futureForecasts().get(0).period());
        assertEquals(nowYm.plusMonths(2), forecast.futureForecasts().get(1).period());
        assertEquals(nowYm.plusMonths(3), forecast.futureForecasts().get(2).period());
    }

    @Test
    @DisplayName("Should rank top spending categories and compute relative shares")
    void testTopSpendingCategories() {
        YearMonth nowYm = YearMonth.now();
        LocalDateTime dt = nowYm.atDay(5).atTime(10, 0);

        storage.saveTransaction(new Transaction("t1", userId, dt, Category.HOUSING, new BigDecimal("1200.00"), PaymentMode.BANK_TRANSFER, TransactionType.EXPENSE, "Rent"));
        storage.saveTransaction(new Transaction("t2", userId, dt, Category.FOOD_AND_DINING, new BigDecimal("600.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Food"));
        storage.saveTransaction(new Transaction("t3", userId, dt, Category.UTILITIES, new BigDecimal("200.00"), PaymentMode.UPI, TransactionType.EXPENSE, "Bills"));

        // Total expense: 2000
        List<CategorySpendingShare> shares = processor.getTopSpendingCategories(userId, 3);
        assertEquals(3, shares.size());

        // #1 Housing: 1200 / 2000 = 60%
        assertEquals(Category.HOUSING, shares.get(0).category());
        assertEquals(0, new BigDecimal("1200.00").compareTo(shares.get(0).totalSpent()));
        assertEquals(60.0, shares.get(0).percentageOfTotal(), 0.01);

        // #2 Food & Dining: 600 / 2000 = 30%
        assertEquals(Category.FOOD_AND_DINING, shares.get(1).category());
        assertEquals(0, new BigDecimal("600.00").compareTo(shares.get(1).totalSpent()));
        assertEquals(30.0, shares.get(1).percentageOfTotal(), 0.01);
    }

    @Test
    @DisplayName("Should evaluate comprehensive financial health summary and compliance score")
    void testHealthSummary() {
        YearMonth nowYm = YearMonth.now();
        LocalDateTime dt = nowYm.atDay(5).atTime(10, 0);

        // Set budget: 1500 for Housing
        budgetManager.setCategoryBudget(userId, Category.HOUSING, new BigDecimal("1500.00"));

        // Inflow: 8000, Outflow: 1200
        storage.saveTransaction(new Transaction("t-inc", userId, dt, Category.SALARY, new BigDecimal("8000.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Paycheck"));
        storage.saveTransaction(new Transaction("t-exp", userId, dt, Category.HOUSING, new BigDecimal("1200.00"), PaymentMode.BANK_TRANSFER, TransactionType.EXPENSE, "Rent"));

        HealthSummary summary = processor.computeHealthSummary(userId);

        assertEquals(0, new BigDecimal("8000.00").compareTo(summary.totalIncome()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(summary.totalExpenses()));
        assertEquals(0, new BigDecimal("6800.00").compareTo(summary.netSavings()));

        // Savings rate = (6800 / 8000) * 100 = 85.0%
        assertEquals(85.0, summary.savingsRate(), 0.01);

        // Score should be high (>= 85) -> "EXCELLENT"
        assertTrue(summary.budgetComplianceScore() >= 85);
        assertEquals("EXCELLENT", summary.healthRating());
        assertNotNull(summary.actionableInsight());
    }
}
