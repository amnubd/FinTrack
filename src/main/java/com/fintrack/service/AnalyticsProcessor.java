package com.fintrack.service;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.service.dto.CategoryRunRate;
import com.fintrack.service.dto.CategorySpendingShare;
import com.fintrack.service.dto.ForecastResult;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.service.dto.MonthlyForecast;
import com.fintrack.service.dto.VelocityMetrics;
import com.fintrack.storage.StorageEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computational analytics engine deriving transaction velocity, burn rates,
 * predictive balance forecasting, category exposure shares, and financial health scores.
 */
public class AnalyticsProcessor {

    private static final int DEFAULT_VELOCITY_DAYS = 30;
    private static final int DEFAULT_FORECAST_MONTHS = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StorageEngine storageEngine;
    private final BudgetManager budgetManager;

    /**
     * Constructs an AnalyticsProcessor with a dedicated StorageEngine.
     * Initializes an internal BudgetManager to support budget-correlated calculations.
     *
     * @param storageEngine active persistence provider; must not be null
     */
    public AnalyticsProcessor(StorageEngine storageEngine) {
        this(storageEngine, new BudgetManager(storageEngine));
    }

    /**
     * Constructs an AnalyticsProcessor with explicitly injected storage and budget services.
     *
     * @param storageEngine active persistence provider; must not be null
     * @param budgetManager active budget manager; must not be null
     */
    public AnalyticsProcessor(StorageEngine storageEngine, BudgetManager budgetManager) {
        this.storageEngine = Objects.requireNonNull(storageEngine, "StorageEngine cannot be null");
        this.budgetManager = Objects.requireNonNull(budgetManager, "BudgetManager cannot be null");
    }

    // =========================================================================
    // 1. Transaction Velocity
    // =========================================================================

    /**
     * Computes the user's transaction frequency and spending velocity over the default 30-day window.
     *
     * @param userId user identifier
     * @return {@link VelocityMetrics} containing daily and weekly frequency/volume rates
     */
    public VelocityMetrics computeVelocity(String userId) {
        return computeVelocity(userId, DEFAULT_VELOCITY_DAYS);
    }

    /**
     * Computes the user's transaction frequency and spending velocity over a specified day window.
     *
     * @param userId       user identifier
     * @param daysToInspect number of past calendar days to analyze (must be >= 1)
     * @return {@link VelocityMetrics} containing daily and weekly frequency/volume rates
     */
    public VelocityMetrics computeVelocity(String userId, int daysToInspect) {
        int window = Math.max(1, daysToInspect);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(window);

        List<Transaction> transactions = storageEngine.getTransactionsByDateRange(userId, windowStart, now);
        if (transactions.isEmpty()) {
            return VelocityMetrics.empty();
        }

        int totalTxCount = transactions.size();

        BigDecimal totalSpend = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double dailyTxCount = (double) totalTxCount / window;
        BigDecimal dailySpend = totalSpend.divide(BigDecimal.valueOf(window), 2, RoundingMode.HALF_UP);

        double weeklyTxCount = dailyTxCount * 7.0;
        BigDecimal weeklySpend = dailySpend.multiply(BigDecimal.valueOf(7)).setScale(2, RoundingMode.HALF_UP);

        return new VelocityMetrics(
                dailyTxCount,
                dailySpend,
                weeklyTxCount,
                weeklySpend,
                totalTxCount,
                totalSpend,
                window
        );
    }

    // =========================================================================
    // 2. Category Run-Rates & Month-over-Month (MoM) Variance
    // =========================================================================

    /**
     * Computes the spending run-rate and month-over-month trajectory for all categories in the current month.
     *
     * @param userId user identifier
     * @return list of {@link CategoryRunRate} projections sorted descending by projected month-end spend
     */
    public List<CategoryRunRate> computeCategoryRunRates(String userId) {
        YearMonth now = YearMonth.now();
        return computeCategoryRunRates(userId, now.getMonthValue(), now.getYear());
    }

    /**
     * Computes the spending run-rate and month-over-month trajectory for all categories in a designated period.
     *
     * @param userId user identifier
     * @param month  calendar month (1 to 12)
     * @param year   calendar year
     * @return list of {@link CategoryRunRate} projections sorted descending by projected month-end spend
     */
    public List<CategoryRunRate> computeCategoryRunRates(String userId, int month, int year) {
        YearMonth currentYm = YearMonth.of(year, month);
        int daysInMonth = currentYm.lengthOfMonth();

        int daysElapsed;
        if (currentYm.equals(YearMonth.now())) {
            daysElapsed = Math.min(LocalDate.now().getDayOfMonth(), daysInMonth);
        } else if (currentYm.isBefore(YearMonth.now())) {
            daysElapsed = daysInMonth;
        } else {
            daysElapsed = 1;
        }
        daysElapsed = Math.max(1, daysElapsed);

        YearMonth prevYm = currentYm.minusMonths(1);

        // Fetch current month expense transactions
        LocalDateTime startCurr = currentYm.atDay(1).atStartOfDay();
        LocalDateTime endCurr = currentYm.atEndOfMonth().atTime(23, 59, 59, 999999999);
        Map<Category, BigDecimal> currentSpendMap = storageEngine.getTransactionsByDateRange(userId, startCurr, endCurr).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        // Fetch previous month expense transactions
        LocalDateTime startPrev = prevYm.atDay(1).atStartOfDay();
        LocalDateTime endPrev = prevYm.atEndOfMonth().atTime(23, 59, 59, 999999999);
        Map<Category, BigDecimal> prevSpendMap = storageEngine.getTransactionsByDateRange(userId, startPrev, endPrev).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        Set<Category> allCategories = new HashSet<>(currentSpendMap.keySet());
        allCategories.addAll(prevSpendMap.keySet());

        final int elapsed = daysElapsed;
        return allCategories.stream()
                .map(category -> {
                    BigDecimal currentSpent = currentSpendMap.getOrDefault(category, BigDecimal.ZERO);
                    BigDecimal prevSpent = prevSpendMap.getOrDefault(category, BigDecimal.ZERO);

                    BigDecimal dailyRunRate = currentSpent.divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
                    BigDecimal projectedMonthlySpend = dailyRunRate.multiply(BigDecimal.valueOf(daysInMonth)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal absoluteChange = projectedMonthlySpend.subtract(prevSpent);

                    double percentageChange = 0.0;
                    if (prevSpent.compareTo(BigDecimal.ZERO) > 0) {
                        percentageChange = absoluteChange.divide(prevSpent, 4, RoundingMode.HALF_UP)
                                .multiply(HUNDRED)
                                .doubleValue();
                    } else if (projectedMonthlySpend.compareTo(BigDecimal.ZERO) > 0) {
                        percentageChange = 100.0;
                    }

                    return new CategoryRunRate(
                            category,
                            currentSpent,
                            dailyRunRate,
                            projectedMonthlySpend,
                            prevSpent,
                            absoluteChange,
                            percentageChange
                    );
                })
                .sorted(Comparator.comparing(CategoryRunRate::projectedMonthlySpend).reversed())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 3. Balance Forecasting (Current Month & Next 3 Months)
    // =========================================================================

    /**
     * Forecasts cash balances for the remainder of the current month and the following 3 forward months.
     *
     * @param userId user identifier
     * @return {@link ForecastResult} with real-time balance, month-end projection, and forward projections
     */
    public ForecastResult computeBalanceForecast(String userId) {
        return computeBalanceForecast(userId, DEFAULT_FORECAST_MONTHS);
    }

    /**
     * Forecasts cash balances for the remainder of the current month and N forward months.
     *
     * @param userId        user identifier
     * @param forwardMonths number of forward months to project
     * @return {@link ForecastResult} containing real-time balance and forward projections
     */
    public ForecastResult computeBalanceForecast(String userId, int forwardMonths) {
        int months = Math.max(1, forwardMonths);
        BigDecimal currentBalance = computeCurrentBalance(userId);

        YearMonth nowYm = YearMonth.now();
        int totalDaysInMonth = nowYm.lengthOfMonth();
        int dayOfMonth = Math.min(LocalDate.now().getDayOfMonth(), totalDaysInMonth);
        int remainingDays = Math.max(0, totalDaysInMonth - dayOfMonth);
        int elapsedDays = Math.max(1, dayOfMonth);

        // Fetch current month-to-date data
        LocalDateTime startCurr = nowYm.atDay(1).atStartOfDay();
        LocalDateTime endCurr = nowYm.atEndOfMonth().atTime(23, 59, 59, 999999999);
        List<Transaction> mtdTxs = storageEngine.getTransactionsByDateRange(userId, startCurr, endCurr);

        BigDecimal mtdIncome = mtdTxs.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal mtdExpense = mtdTxs.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Optional<User> userOpt = storageEngine.getUserById(userId);
        BigDecimal expectedMonthlyIncome = userOpt.map(User::getMonthlyIncome).orElse(BigDecimal.ZERO);

        // Daily Run-Rate calculations
        BigDecimal dailyIncomeRate;
        if (mtdIncome.compareTo(BigDecimal.ZERO) > 0) {
            dailyIncomeRate = mtdIncome.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
        } else if (expectedMonthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            dailyIncomeRate = expectedMonthlyIncome.divide(BigDecimal.valueOf(totalDaysInMonth), 2, RoundingMode.HALF_UP);
        } else {
            dailyIncomeRate = BigDecimal.ZERO;
        }

        BigDecimal dailyExpenseRate = mtdExpense.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);

        BigDecimal projectedRemainingNet = dailyIncomeRate.subtract(dailyExpenseRate)
                .multiply(BigDecimal.valueOf(remainingDays))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal projectedCurrentMonthEnd = currentBalance.add(projectedRemainingNet);

        // Future Monthly Forecasts
        BigDecimal baselineMonthlyIncome = (expectedMonthlyIncome.compareTo(BigDecimal.ZERO) > 0)
                ? expectedMonthlyIncome
                : dailyIncomeRate.multiply(BigDecimal.valueOf(30)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal baselineMonthlyExpense = dailyExpenseRate.multiply(BigDecimal.valueOf(30)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedMonthlyNet = baselineMonthlyIncome.subtract(baselineMonthlyExpense);

        List<MonthlyForecast> futureForecasts = new ArrayList<>();
        BigDecimal runningBalance = projectedCurrentMonthEnd;

        for (int i = 1; i <= months; i++) {
            YearMonth targetPeriod = nowYm.plusMonths(i);
            runningBalance = runningBalance.add(projectedMonthlyNet);
            futureForecasts.add(new MonthlyForecast(
                    targetPeriod,
                    baselineMonthlyIncome,
                    baselineMonthlyExpense,
                    projectedMonthlyNet,
                    runningBalance
            ));
        }

        return new ForecastResult(currentBalance, projectedCurrentMonthEnd, futureForecasts);
    }

    /**
     * Computes the actual net available liquidity balance across all recorded transactions.
     *
     * @param userId user identifier
     * @return current cash balance
     */
    public BigDecimal computeCurrentBalance(String userId) {
        List<Transaction> transactions = storageEngine.getTransactionsByUserId(userId);
        BigDecimal totalIncome = transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalIncome.subtract(totalExpense);
    }

    // =========================================================================
    // 4. Top Spending Categories
    // =========================================================================

    /**
     * Returns top spending categories for the current month ranked by expenditure volume.
     *
     * @param userId user identifier
     * @param limit  maximum number of categories to return
     * @return list of {@link CategorySpendingShare}
     */
    public List<CategorySpendingShare> getTopSpendingCategories(String userId, int limit) {
        YearMonth now = YearMonth.now();
        return getTopSpendingCategories(userId, now.getMonthValue(), now.getYear(), limit);
    }

    /**
     * Returns top spending categories for a designated period ranked by expenditure volume.
     *
     * @param userId user identifier
     * @param month  calendar month (1 to 12)
     * @param year   calendar year
     * @param limit  maximum number of categories to return
     * @return list of {@link CategorySpendingShare}
     */
    public List<CategorySpendingShare> getTopSpendingCategories(String userId, int month, int year, int limit) {
        YearMonth period = YearMonth.of(year, month);
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.atEndOfMonth().atTime(23, 59, 59, 999999999);

        List<Transaction> expenses = storageEngine.getTransactionsByDateRange(userId, start, end).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.toList());

        if (expenses.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal grandTotal = expenses.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Category, List<Transaction>> grouped = expenses.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory));

        return grouped.entrySet().stream()
                .map(entry -> {
                    Category cat = entry.getKey();
                    List<Transaction> txList = entry.getValue();
                    BigDecimal catTotal = txList.stream()
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    double share = 0.0;
                    if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                        share = catTotal.divide(grandTotal, 4, RoundingMode.HALF_UP)
                                .multiply(HUNDRED)
                                .doubleValue();
                    }

                    return new CategorySpendingShare(cat, catTotal, share, txList.size());
                })
                .sorted(Comparator.comparing(CategorySpendingShare::totalSpent).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 5. Financial Health Summary & Composite Scoring
    // =========================================================================

    /**
     * Evaluates high-level financial wellness, savings efficiency, and budget compliance for the active month.
     *
     * @param userId user identifier
     * @return {@link HealthSummary}
     */
    public HealthSummary computeHealthSummary(String userId) {
        YearMonth now = YearMonth.now();
        return computeHealthSummary(userId, now.getMonthValue(), now.getYear());
    }

    /**
     * Evaluates high-level financial wellness, savings efficiency, and budget compliance for a given month.
     *
     * @param userId user identifier
     * @param month  calendar month (1 to 12)
     * @param year   calendar year
     * @return {@link HealthSummary}
     */
    public HealthSummary computeHealthSummary(String userId, int month, int year) {
        YearMonth period = YearMonth.of(year, month);
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.atEndOfMonth().atTime(23, 59, 59, 999999999);

        List<Transaction> txs = storageEngine.getTransactionsByDateRange(userId, start, end);

        BigDecimal actualIncome = txs.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualExpenses = txs.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Optional<User> userOpt = storageEngine.getUserById(userId);
        BigDecimal effectiveIncome = (actualIncome.compareTo(BigDecimal.ZERO) > 0)
                ? actualIncome
                : userOpt.map(User::getMonthlyIncome).orElse(BigDecimal.ZERO);

        BigDecimal netSavings = effectiveIncome.subtract(actualExpenses);

        double savingsRate = 0.0;
        if (effectiveIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netSavings.divide(effectiveIncome, 4, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                    .doubleValue();
        } else if (actualExpenses.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = -100.0;
        }

        // --- Composite Compliance Score (0 to 100) ---
        // 1. Category Budgets Adherence (Weight: 40%)
        List<Budget> budgets = budgetManager.getBudgetsForPeriod(userId, month, year);
        double categoryScore = 40.0;
        if (!budgets.isEmpty()) {
            Map<Category, BigDecimal> spendMap = budgetManager.getMonthlySpendingByCategory(userId, month, year);
            long compliantCount = budgets.stream()
                    .filter(b -> spendMap.getOrDefault(b.getCategory(), BigDecimal.ZERO).compareTo(b.getMonthlyLimit()) <= 0)
                    .count();
            categoryScore = ((double) compliantCount / budgets.size()) * 40.0;
        }

        // 2. Global Macro Budget Adherence (Weight: 30%)
        double globalScore = 30.0;
        BigDecimal globalBudget = userOpt.map(User::getGlobalBudget).orElse(BigDecimal.ZERO);
        if (globalBudget.compareTo(BigDecimal.ZERO) > 0) {
            if (actualExpenses.compareTo(globalBudget) > 0) {
                BigDecimal overage = actualExpenses.subtract(globalBudget);
                double overageRatio = overage.divide(globalBudget, 4, RoundingMode.HALF_UP).doubleValue();
                globalScore = Math.max(0.0, 30.0 - (overageRatio * 30.0));
            }
        } else if (effectiveIncome.compareTo(BigDecimal.ZERO) > 0) {
            if (actualExpenses.compareTo(effectiveIncome) > 0) {
                globalScore = 10.0;
            }
        }

        // 3. Savings Rate Performance (Weight: 30%)
        // Benchmark: 20% savings rate (standard 50/30/20 rule) achieves full 30 points
        double savingsScore = 0.0;
        if (savingsRate >= 20.0) {
            savingsScore = 30.0;
        } else if (savingsRate > 0) {
            savingsScore = (savingsRate / 20.0) * 30.0;
        }

        int compositeScore = (int) Math.round(Math.max(0, Math.min(100, categoryScore + globalScore + savingsScore)));

        // Determine Health Tier
        String healthRating;
        if (compositeScore >= 85) {
            healthRating = "EXCELLENT";
        } else if (compositeScore >= 70) {
            healthRating = "HEALTHY";
        } else if (compositeScore >= 50) {
            healthRating = "MODERATE";
        } else {
            healthRating = "CRITICAL";
        }

        // Actionable Coaching Insight
        String actionableInsight = buildActionableInsight(savingsRate, compositeScore, actualExpenses, effectiveIncome, budgets, userId);

        return new HealthSummary(
                effectiveIncome,
                actualExpenses,
                netSavings,
                savingsRate,
                compositeScore,
                healthRating,
                actionableInsight
        );
    }

    private String buildActionableInsight(double savingsRate, int score, BigDecimal expenses, BigDecimal income, List<Budget> budgets, String userId) {
        if (expenses.compareTo(income) > 0 && income.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("Outflows exceed inflows by %s. Immediate discretionary spending freeze recommended.",
                    expenses.subtract(income).toPlainString());
        }

        List<CategorySpendingShare> topCategories = getTopSpendingCategories(userId, 1);
        String topCategoryName = topCategories.isEmpty() ? "General" : topCategories.get(0).category().getDisplayName();

        if (score < 50) {
            return String.format("Spending velocity is elevated. Focus on curtailing %s expenditures to restore reserves.", topCategoryName);
        } else if (score < 70) {
            return String.format("Moderate discipline maintained. %s represents your largest outflow this period.", topCategoryName);
        } else if (savingsRate >= 20.0) {
            return String.format("Outstanding discipline! You are retaining %.1f%% of income in line with target milestone trajectories.", savingsRate);
        } else {
            return "Finances are stable. Consider increasing milestone allocations to lift savings rate toward the 20% benchmark.";
        }
    }
}
