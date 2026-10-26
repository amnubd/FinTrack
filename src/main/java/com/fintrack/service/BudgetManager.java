package com.fintrack.service;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.ValidationException;
import com.fintrack.storage.StorageEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core business service managing category-level spending allocations, real-time budget compliance,
 * and financial milestone goal tracking.
 * <p>
 * Evaluates real-time expenditures against dynamic category ceilings and provides early warning
 * indicators when spending encroaches on allocated limits.
 * </p>
 */
public class BudgetManager {

    private final StorageEngine storageEngine;

    /**
     * Constructs a BudgetManager with an injected {@link StorageEngine} persistence provider.
     *
     * @param storageEngine active persistence engine; must not be null
     * @throws NullPointerException if storageEngine is null
     */
    public BudgetManager(StorageEngine storageEngine) {
        this.storageEngine = Objects.requireNonNull(storageEngine, "StorageEngine cannot be null");
    }

    // =========================================================================
    // Budget Allocation & Management
    // =========================================================================

    /**
     * Sets or updates a recurring monthly spending limit for a specific category and billing cycle.
     * If a budget already exists for the given period, it is updated in-place; otherwise, a new
     * record is saved.
     *
     * @param userId       owning user ID; must not be null or blank
     * @param category     target category; must not be null
     * @param monthlyLimit spending limit; must be strictly greater than 0
     * @param month        calendar month (1 to 12)
     * @param year         calendar year (>= 2000)
     * @return the persisted or updated {@link Budget} instance
     * @throws ValidationException if validation rules are violated
     */
    public Budget setCategoryBudget(String userId,
                                   Category category,
                                   BigDecimal monthlyLimit,
                                   int month,
                                   int year) {
        Objects.requireNonNull(category, "Category cannot be null");
        if (monthlyLimit == null || monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Monthly limit must be strictly positive (> 0). Received: " + monthlyLimit);
        }

        Optional<Budget> existing = storageEngine.getBudgetByUserAndCategory(userId, category, month, year);
        if (existing.isPresent()) {
            Budget budget = existing.get();
            budget.setMonthlyLimit(monthlyLimit);
            storageEngine.updateBudget(budget);
            return budget;
        } else {
            Budget newBudget = new Budget(userId, category, monthlyLimit);
            newBudget.setMonth(month);
            newBudget.setYear(year);
            storageEngine.saveBudget(newBudget);
            return newBudget;
        }
    }

    /**
     * Sets or updates a category budget limit for the current calendar month and year.
     *
     * @param userId       owning user ID
     * @param category     target category
     * @param monthlyLimit spending limit
     * @return the persisted {@link Budget} instance
     */
    public Budget setCategoryBudget(String userId, Category category, BigDecimal monthlyLimit) {
        YearMonth now = YearMonth.now();
        return setCategoryBudget(userId, category, monthlyLimit, now.getMonthValue(), now.getYear());
    }

    /**
     * Retrieves the budget limit allocated to a specific category and period.
     *
     * @param userId   owning user ID
     * @param category category to inspect
     * @param month    calendar month (1 - 12)
     * @param year     calendar year
     * @return an {@link Optional} containing the budget if configured, or empty
     */
    public Optional<Budget> getCategoryBudget(String userId, Category category, int month, int year) {
        return storageEngine.getBudgetByUserAndCategory(userId, category, month, year);
    }

    /**
     * Retrieves all configured category budgets for a user for a specific month and year.
     *
     * @param userId user identifier
     * @param month  calendar month
     * @param year   calendar year
     * @return list of matching budgets
     */
    public List<Budget> getBudgetsForPeriod(String userId, int month, int year) {
        return storageEngine.getBudgetsByUserId(userId).stream()
                .filter(b -> b.getMonth() == month && b.getYear() == year)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a category budget configuration by identifier.
     *
     * @param budgetId unique budget ID
     */
    public void deleteBudget(String budgetId) {
        storageEngine.deleteBudget(budgetId);
    }

    // =========================================================================
    // Real-Time Spending Computations
    // =========================================================================

    /**
     * Computes the aggregated real-time expense outflow for a specific category within the current month.
     *
     * @param userId   user ID
     * @param category category to calculate
     * @return total expenditure incurred in the category for the current month
     */
    public BigDecimal getSpendingForCategory(String userId, Category category) {
        YearMonth now = YearMonth.now();
        return getSpendingForCategory(userId, category, now.getMonthValue(), now.getYear());
    }

    /**
     * Computes the aggregated real-time expense outflow for a specific category and period.
     *
     * @param userId   user ID
     * @param category category to calculate
     * @param month    calendar month (1 - 12)
     * @param year     calendar year
     * @return total expenditure incurred in the category for the given month
     */
    public BigDecimal getSpendingForCategory(String userId, Category category, int month, int year) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59, 999999999);

        return storageEngine.getTransactionsByDateRange(userId, start, end).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getCategory() == category)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates real-time total expenditures grouped by category for the current month.
     *
     * @param userId user ID
     * @return map linking each active expense category to its total spending
     */
    public Map<Category, BigDecimal> getMonthlySpendingByCategory(String userId) {
        YearMonth now = YearMonth.now();
        return getMonthlySpendingByCategory(userId, now.getMonthValue(), now.getYear());
    }

    /**
     * Calculates real-time total expenditures grouped by category for a designated period.
     *
     * @param userId user ID
     * @param month  calendar month (1 - 12)
     * @param year   calendar year
     * @return map linking each active expense category to its total spending
     */
    public Map<Category, BigDecimal> getMonthlySpendingByCategory(String userId, int month, int year) {
        LocalDateTime start = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime end = YearMonth.of(year, month).atEndOfMonth().atTime(23, 59, 59, 999999999);

        return storageEngine.getTransactionsByDateRange(userId, start, end).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));
    }

    // =========================================================================
    // Budget Compliance Status & Reporting
    // =========================================================================

    /**
     * Returns the real-time {@link BudgetStatus} for a category in the current month.
     *
     * @param userId   user ID
     * @param category category to inspect
     * @return evaluated {@link BudgetStatus}
     */
    public BudgetStatus getBudgetStatus(String userId, Category category) {
        YearMonth now = YearMonth.now();
        return getBudgetStatus(userId, category, now.getMonthValue(), now.getYear());
    }

    /**
     * Returns the real-time {@link BudgetStatus} for a category in a designated month/year.
     * <ul>
     *     <li>{@link BudgetStatus#OK}: spend &lt; 75% of limit</li>
     *     <li>{@link BudgetStatus#WARNING}: 75% &le; spend &le; 100% of limit</li>
     *     <li>{@link BudgetStatus#EXCEEDED}: spend &gt; 100% of limit</li>
     * </ul>
     *
     * @param userId   user ID
     * @param category category to inspect
     * @param month    calendar month
     * @param year     calendar year
     * @return evaluated {@link BudgetStatus}
     */
    public BudgetStatus getBudgetStatus(String userId, Category category, int month, int year) {
        BigDecimal spent = getSpendingForCategory(userId, category, month, year);
        Optional<Budget> budgetOpt = getCategoryBudget(userId, category, month, year);

        if (budgetOpt.isEmpty()) {
            return (spent.compareTo(BigDecimal.ZERO) > 0) ? BudgetStatus.EXCEEDED : BudgetStatus.OK;
        }

        return BudgetStatus.evaluate(spent, budgetOpt.get().getMonthlyLimit());
    }

    /**
     * Generates a detailed {@link CategoryBudgetReport} for an individual category for the current month.
     *
     * @param userId   user ID
     * @param category target category
     * @return evaluation report
     */
    public CategoryBudgetReport getCategoryBudgetReport(String userId, Category category) {
        YearMonth now = YearMonth.now();
        return getCategoryBudgetReport(userId, category, now.getMonthValue(), now.getYear());
    }

    /**
     * Generates a detailed {@link CategoryBudgetReport} for an individual category for a specific month and year.
     *
     * @param userId   user ID
     * @param category target category
     * @param month    calendar month
     * @param year     calendar year
     * @return evaluation report
     */
    public CategoryBudgetReport getCategoryBudgetReport(String userId, Category category, int month, int year) {
        BigDecimal spent = getSpendingForCategory(userId, category, month, year);
        BigDecimal limit = getCategoryBudget(userId, category, month, year)
                .map(Budget::getMonthlyLimit)
                .orElse(BigDecimal.ZERO);

        return CategoryBudgetReport.of(category, limit, spent);
    }

    /**
     * Generates compliance reports for all configured category budgets for a user in the current month.
     *
     * @param userId user ID
     * @return list of reports for all budgeted categories
     */
    public List<CategoryBudgetReport> getAllCategoryBudgetReports(String userId) {
        YearMonth now = YearMonth.now();
        return getAllCategoryBudgetReports(userId, now.getMonthValue(), now.getYear());
    }

    /**
     * Generates compliance reports for all configured category budgets for a user in a specific period.
     *
     * @param userId user ID
     * @param month  calendar month
     * @param year   calendar year
     * @return list of reports for all budgeted categories
     */
    public List<CategoryBudgetReport> getAllCategoryBudgetReports(String userId, int month, int year) {
        List<Budget> budgets = getBudgetsForPeriod(userId, month, year);
        Map<Category, BigDecimal> spendingMap = getMonthlySpendingByCategory(userId, month, year);

        return budgets.stream()
                .map(b -> {
                    BigDecimal spent = spendingMap.getOrDefault(b.getCategory(), BigDecimal.ZERO);
                    return CategoryBudgetReport.of(b.getCategory(), b.getMonthlyLimit(), spent);
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Financial Milestone Goal Tracking
    // =========================================================================

    /**
     * Creates and persists a new financial milestone savings goal.
     *
     * @param userId       owning user ID; must not be null or blank
     * @param name         goal title (e.g., "Emergency Fund", "New Laptop")
     * @param targetAmount target monetary threshold; must be > 0
     * @param deadline     target milestone deadline
     * @return the persisted {@link Goal}
     */
    public Goal createGoal(String userId, String name, BigDecimal targetAmount, LocalDate deadline) {
        Goal goal = new Goal(userId, name, targetAmount, deadline);
        storageEngine.saveGoal(goal);
        return goal;
    }

    /**
     * Adds an incremental savings contribution toward an existing goal.
     *
     * @param goalId             unique goal ID
     * @param contributionAmount monetary amount to contribute; must be > 0
     * @return the updated {@link Goal}
     * @throws ValidationException if goal does not exist or contribution <= 0
     */
    public Goal contributeToGoal(String goalId, BigDecimal contributionAmount) {
        if (contributionAmount == null || contributionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Contribution amount must be strictly positive (> 0).");
        }

        Goal goal = storageEngine.getGoalById(goalId)
                .orElseThrow(() -> new ValidationException("Goal with ID [" + goalId + "] not found."));

        BigDecimal updatedAmount = goal.getCurrentAmount().add(contributionAmount);
        goal.setCurrentAmount(updatedAmount);
        storageEngine.updateGoal(goal);
        return goal;
    }

    /**
     * Overrides the current progress amount of a milestone goal.
     *
     * @param goalId           unique goal ID
     * @param newCurrentAmount new non-negative progress amount
     * @return the updated {@link Goal}
     */
    public Goal updateGoalCurrentAmount(String goalId, BigDecimal newCurrentAmount) {
        Goal goal = storageEngine.getGoalById(goalId)
                .orElseThrow(() -> new ValidationException("Goal with ID [" + goalId + "] not found."));

        goal.setCurrentAmount(newCurrentAmount);
        storageEngine.updateGoal(goal);
        return goal;
    }

    /**
     * Retrieves the completion percentage for a milestone goal (0.0% to 100.0%+).
     *
     * @param goalId unique goal ID
     * @return percentage progress completed
     */
    public double getGoalProgress(String goalId) {
        return storageEngine.getGoalById(goalId)
                .map(Goal::getProgressPercentage)
                .orElseThrow(() -> new ValidationException("Goal with ID [" + goalId + "] not found."));
    }

    /**
     * Retrieves all milestone savings goals configured for a user.
     *
     * @param userId user identifier
     * @return list of user goals
     */
    public List<Goal> getUserGoals(String userId) {
        return storageEngine.getGoalsByUserId(userId);
    }

    /**
     * Retrieves a milestone goal by its unique identifier.
     *
     * @param goalId goal ID
     * @return an {@link Optional} containing the goal if found
     */
    public Optional<Goal> getGoal(String goalId) {
        return storageEngine.getGoalById(goalId);
    }

    /**
     * Deletes a milestone goal by its unique identifier.
     *
     * @param goalId goal ID to delete
     */
    public void deleteGoal(String goalId) {
        storageEngine.deleteGoal(goalId);
    }
}
