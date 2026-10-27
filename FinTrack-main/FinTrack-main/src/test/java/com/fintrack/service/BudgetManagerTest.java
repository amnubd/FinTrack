package com.fintrack.service;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BudgetManager Service Unit Tests")
public class BudgetManagerTest {

    private SQLiteStorageEngine storage;
    private BudgetManager budgetManager;
    private final String userId = "user-101";

    @BeforeEach
    void setUp() {
        storage = new SQLiteStorageEngine("jdbc:sqlite::memory:");
        budgetManager = new BudgetManager(storage);

        User user = new User(userId, "Jane Tester", "jane@example.com", "USD",
                new BigDecimal("6000.00"), new BigDecimal("3000.00"), new BigDecimal("1000.00"));
        storage.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("Should set and update recurring monthly budget limits")
    void testSetAndUpdateBudget() {
        YearMonth now = YearMonth.now();

        // Initial budget: 500 for FOOD_AND_DINING
        Budget b1 = budgetManager.setCategoryBudget(userId, Category.FOOD_AND_DINING, new BigDecimal("500.00"));
        assertEquals(0, new BigDecimal("500.00").compareTo(b1.getMonthlyLimit()));

        // Update in-place: change to 650 for same month
        Budget b2 = budgetManager.setCategoryBudget(userId, Category.FOOD_AND_DINING, new BigDecimal("650.00"), now.getMonthValue(), now.getYear());
        assertEquals(0, new BigDecimal("650.00").compareTo(b2.getMonthlyLimit()));

        // Verify only 1 budget exists in storage for this category and month
        List<Budget> budgets = budgetManager.getBudgetsForPeriod(userId, now.getMonthValue(), now.getYear());
        assertEquals(1, budgets.size());
        assertEquals(0, new BigDecimal("650.00").compareTo(budgets.get(0).getMonthlyLimit()));
    }

    @Test
    @DisplayName("Should calculate real-time spending for current month")
    void testRealtimeSpendingCalculation() {
        YearMonth now = YearMonth.now();
        LocalDateTime midMonth = now.atDay(15).atTime(12, 0);

        // Expense in current month: 120.00
        storage.saveTransaction(new Transaction("tx-1", userId, midMonth, Category.GROCERIES,
                new BigDecimal("120.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Grocery run 1"));

        // Expense in current month: 80.50
        storage.saveTransaction(new Transaction("tx-2", userId, midMonth.plusDays(1), Category.GROCERIES,
                new BigDecimal("80.50"), PaymentMode.UPI, TransactionType.EXPENSE, "Grocery run 2"));

        // Income in current month: should NOT count towards expense spending
        storage.saveTransaction(new Transaction("tx-3", userId, midMonth, Category.SALARY,
                new BigDecimal("5000.00"), PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Salary"));

        // Expense in previous month: should NOT count towards current month
        storage.saveTransaction(new Transaction("tx-4", userId, midMonth.minusMonths(1), Category.GROCERIES,
                new BigDecimal("300.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Old groceries"));

        BigDecimal groceriesSpent = budgetManager.getSpendingForCategory(userId, Category.GROCERIES);
        assertEquals(0, new BigDecimal("200.50").compareTo(groceriesSpent));

        Map<Category, BigDecimal> spendingMap = budgetManager.getMonthlySpendingByCategory(userId);
        assertEquals(0, new BigDecimal("200.50").compareTo(spendingMap.get(Category.GROCERIES)));
        assertNull(spendingMap.get(Category.SALARY)); // Incomes filtered out
    }

    @Test
    @DisplayName("Should evaluate BudgetStatus thresholds: OK (<75%), WARNING (75-100%), EXCEEDED (>100%)")
    void testBudgetStatusEvaluation() {
        YearMonth now = YearMonth.now();
        LocalDateTime txDate = now.atDay(10).atTime(10, 0);

        // Set budget: 1000.00 for SHOPPING
        budgetManager.setCategoryBudget(userId, Category.SHOPPING, new BigDecimal("1000.00"));

        // 1. Spend 500 (50%) -> OK (< 75%)
        storage.saveTransaction(new Transaction("tx-s1", userId, txDate, Category.SHOPPING,
                new BigDecimal("500.00"), PaymentMode.CARD, TransactionType.EXPENSE, "New clothes"));
        assertEquals(BudgetStatus.OK, budgetManager.getBudgetStatus(userId, Category.SHOPPING));

        // 2. Spend additional 300 (Total 800 = 80%) -> WARNING (75% to 100%)
        storage.saveTransaction(new Transaction("tx-s2", userId, txDate.plusDays(1), Category.SHOPPING,
                new BigDecimal("300.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Shoes"));
        assertEquals(BudgetStatus.WARNING, budgetManager.getBudgetStatus(userId, Category.SHOPPING));

        // 3. Spend additional 200 (Total 1000 = 100%) -> WARNING (75% to 100% inclusive)
        storage.saveTransaction(new Transaction("tx-s3", userId, txDate.plusDays(2), Category.SHOPPING,
                new BigDecimal("200.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Accessories"));
        assertEquals(BudgetStatus.WARNING, budgetManager.getBudgetStatus(userId, Category.SHOPPING));

        // 4. Spend additional 50 (Total 1050 = 105%) -> EXCEEDED (> 100%)
        storage.saveTransaction(new Transaction("tx-s4", userId, txDate.plusDays(3), Category.SHOPPING,
                new BigDecimal("50.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Over budget purchase"));
        assertEquals(BudgetStatus.EXCEEDED, budgetManager.getBudgetStatus(userId, Category.SHOPPING));

        // Verify CategoryBudgetReport
        CategoryBudgetReport report = budgetManager.getCategoryBudgetReport(userId, Category.SHOPPING);
        assertEquals(0, new BigDecimal("1000.00").compareTo(report.monthlyLimit()));
        assertEquals(0, new BigDecimal("1050.00").compareTo(report.spentAmount()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(report.remainingAmount()));
        assertEquals(105.0, report.percentageUsed(), 0.01);
        assertEquals(BudgetStatus.EXCEEDED, report.status());
    }

    @Test
    @DisplayName("Should track financial milestone goals, contributions, and progress")
    void testMilestoneGoalTracking() {
        LocalDate deadline = LocalDate.now().plusMonths(6);

        // 1. Create Goal
        Goal goal = budgetManager.createGoal(userId, "Emergency Fund", new BigDecimal("10000.00"), deadline);
        assertNotNull(goal.getId());
        assertEquals(0.0, budgetManager.getGoalProgress(goal.getId()), 0.001);

        // 2. Contribute 2500 -> 25% progress
        budgetManager.contributeToGoal(goal.getId(), new BigDecimal("2500.00"));
        assertEquals(25.0, budgetManager.getGoalProgress(goal.getId()), 0.001);

        // 3. Contribute another 5000 -> 75% progress
        budgetManager.contributeToGoal(goal.getId(), new BigDecimal("5000.00"));
        assertEquals(75.0, budgetManager.getGoalProgress(goal.getId()), 0.001);

        // 4. Verify user goals retrieval
        List<Goal> goals = budgetManager.getUserGoals(userId);
        assertEquals(1, goals.size());
        assertEquals("Emergency Fund", goals.get(0).getName());
        assertEquals(0, new BigDecimal("7500.00").compareTo(goals.get(0).getCurrentAmount()));

        // 5. Verify savings transactions recorded in storage (DATA-01)
        List<Transaction> txs = storage.getTransactionsByUserId(userId);
        assertEquals(2, txs.size());
        assertTrue(txs.stream().allMatch(t -> t.getCategory() == Category.SAVINGS && t.getType() == TransactionType.EXPENSE));
    }
}
