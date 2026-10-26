package com.fintrack.util;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.storage.StorageEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Utility data seeder that populates realistic 3-month personal financial records
 * denominated in Indian Rupees (INR - ₹), complete with recurring salaries, rent,
 * utility bills, discretionary outflows, category budgets, and savings milestone targets.
 */
public final class SeedData {

    public static final String DEFAULT_USER_ID = "user_inr_1";

    private SeedData() {}

    /**
     * Seeds sample data only if the storage engine currently contains zero users.
     *
     * @param storage storage engine instance to inspect and seed
     */
    public static void seedIfEmpty(StorageEngine storage) {
        if (storage == null) return;
        List<User> existing = storage.getAllUsers();
        if (existing.isEmpty()) {
            seed(storage, false);
        }
    }

    /**
     * Populates 3 months of realistic financial data in Indian Rupees (INR).
     *
     * @param storage   target storage engine
     * @param overwrite whether to purge existing records for the demo user
     */
    public static void seed(StorageEngine storage, boolean overwrite) {
        if (storage == null) return;

        if (overwrite) {
            storage.deleteUser(DEFAULT_USER_ID);
        }

        // 1. Primary User in INR
        User user = new User(
                DEFAULT_USER_ID,
                "Amnubd",
                "amnubd@fintrack.in",
                "INR",
                new BigDecimal("85000.00"), // Monthly regular income
                new BigDecimal("55000.00"), // Global budget ceiling
                new BigDecimal("25000.00")  // Monthly target savings
        );
        storage.saveUser(user);

        // Reference Months: M-2 (two months ago), M-1 (last month), M-0 (current month)
        YearMonth currentYm = YearMonth.now();
        YearMonth lastYm = currentYm.minusMonths(1);
        YearMonth twoMonthsAgoYm = currentYm.minusMonths(2);

        List<YearMonth> quarters = List.of(twoMonthsAgoYm, lastYm, currentYm);

        // 2. Seed Monthly Recurring Budgets
        for (YearMonth ym : quarters) {
            int m = ym.getMonthValue();
            int y = ym.getYear();

            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.HOUSING, new BigDecimal("25000.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.GROCERIES, new BigDecimal("14000.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.FOOD_AND_DINING, new BigDecimal("6000.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.UTILITIES, new BigDecimal("5000.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.TRANSPORTATION, new BigDecimal("4500.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.SHOPPING, new BigDecimal("6000.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.ENTERTAINMENT, new BigDecimal("3000.00"), m, y));
            storage.saveBudget(new Budget(UUID.randomUUID().toString(), DEFAULT_USER_ID, Category.HEALTHCARE, new BigDecimal("2500.00"), m, y));
        }

        // 3. Seed Realistic Transactions across 3 Months
        for (YearMonth ym : quarters) {
            boolean isCurrentMonth = ym.equals(currentYm);
            int maxDay = isCurrentMonth ? Math.min(LocalDate.now().getDayOfMonth(), ym.lengthOfMonth()) : ym.lengthOfMonth();

            // Inflows: Salary
            addTx(storage, ym, 1, Category.SALARY, "85000.00", PaymentMode.BANK_TRANSFER, TransactionType.INCOME, "Monthly Net Salary Credited");

            // Secondary Inflow: Freelance Tech Consulting
            if (maxDay >= 15) {
                addTx(storage, ym, 15, Category.FREELANCE, "15000.00", PaymentMode.UPI, TransactionType.INCOME, "React/Java Backend Consulting Retainer");
            }

            // Fixed Outflows: Rent
            if (maxDay >= 3) {
                addTx(storage, ym, 3, Category.HOUSING, "22500.00", PaymentMode.BANK_TRANSFER, TransactionType.EXPENSE, "Apartment Monthly Rent & Maintenance");
            }

            // Utilities: Electricity & Fiber
            if (maxDay >= 7) {
                addTx(storage, ym, 7, Category.UTILITIES, "3850.00", PaymentMode.UPI, TransactionType.EXPENSE, "Electricity & Gigabit Fiber Broadband Bill");
            }

            // Groceries - Distributed across month
            if (maxDay >= 4) {
                addTx(storage, ym, 4, Category.GROCERIES, "3450.00", PaymentMode.CARD, TransactionType.EXPENSE, "Supermarket provisions & staples");
            }
            if (maxDay >= 11) {
                addTx(storage, ym, 11, Category.GROCERIES, "4120.00", PaymentMode.UPI, TransactionType.EXPENSE, "Zepto / BigBasket weekly organics");
            }
            if (maxDay >= 18) {
                addTx(storage, ym, 18, Category.GROCERIES, "2890.00", PaymentMode.UPI, TransactionType.EXPENSE, "Fruits, dairy & pantry top-up");
            }
            if (maxDay >= 25) {
                addTx(storage, ym, 25, Category.GROCERIES, "3150.00", PaymentMode.CARD, TransactionType.EXPENSE, "End-of-month grocery restocking");
            }

            // Food & Dining
            if (maxDay >= 6) {
                addTx(storage, ym, 6, Category.FOOD_AND_DINING, "850.00", PaymentMode.UPI, TransactionType.EXPENSE, "Swiggy dinner order");
            }
            if (maxDay >= 14) {
                addTx(storage, ym, 14, Category.FOOD_AND_DINING, "2450.00", PaymentMode.CARD, TransactionType.EXPENSE, "Weekend team bistro dinner");
            }
            if (maxDay >= 21) {
                addTx(storage, ym, 21, Category.FOOD_AND_DINING, "1250.00", PaymentMode.UPI, TransactionType.EXPENSE, "Cafe work session & artisanal coffee");
            }

            // Transportation
            if (maxDay >= 5) {
                addTx(storage, ym, 5, Category.TRANSPORTATION, "2000.00", PaymentMode.UPI, TransactionType.EXPENSE, "Monthly Metro Smart Card Recharge");
            }
            if (maxDay >= 16) {
                addTx(storage, ym, 16, Category.TRANSPORTATION, "1800.00", PaymentMode.CARD, TransactionType.EXPENSE, "Fuel refill for car");
            }

            // Entertainment
            if (maxDay >= 10) {
                addTx(storage, ym, 10, Category.ENTERTAINMENT, "1199.00", PaymentMode.CARD, TransactionType.EXPENSE, "Netflix & Spotify Premium Annual Subscription");
            }
            if (maxDay >= 22) {
                addTx(storage, ym, 22, Category.ENTERTAINMENT, "1450.00", PaymentMode.UPI, TransactionType.EXPENSE, "BookMyShow weekend IMAX tickets");
            }

            // Shopping
            if (maxDay >= 12) {
                addTx(storage, ym, 12, Category.SHOPPING, "3499.00", PaymentMode.CARD, TransactionType.EXPENSE, "Amazon electronics & ergonomic keyboard");
            }

            // Healthcare
            if (maxDay >= 20) {
                addTx(storage, ym, 20, Category.HEALTHCARE, "1250.00", PaymentMode.UPI, TransactionType.EXPENSE, "Apollo Pharmacy wellness & vitamins");
            }
        }

        // 4. Seed Milestone Goals in INR
        storage.saveGoal(new Goal(
                UUID.randomUUID().toString(),
                DEFAULT_USER_ID,
                "Emergency Reserve (6 Months)",
                new BigDecimal("300000.00"), // ₹3 Lakhs
                new BigDecimal("185000.00"),
                LocalDate.now().plusMonths(10)
        ));

        storage.saveGoal(new Goal(
                UUID.randomUUID().toString(),
                DEFAULT_USER_ID,
                "MacBook Pro M-Series",
                new BigDecimal("140000.00"), // ₹1.4 Lakhs
                new BigDecimal("95000.00"),
                LocalDate.now().plusMonths(4)
        ));

        storage.saveGoal(new Goal(
                UUID.randomUUID().toString(),
                DEFAULT_USER_ID,
                "Himachal Backpacking Trek",
                new BigDecimal("45000.00"),  // ₹45,000
                new BigDecimal("32500.00"),
                LocalDate.now().plusMonths(3)
        ));
    }

    private static void addTx(StorageEngine storage, YearMonth ym, int day, Category cat, String amount, PaymentMode mode, TransactionType type, String note) {
        LocalDateTime dt = ym.atDay(day).atTime(12, 30);
        storage.saveTransaction(new Transaction(
                UUID.randomUUID().toString(),
                DEFAULT_USER_ID,
                dt,
                cat,
                new BigDecimal(amount),
                mode,
                type,
                note
        ));
    }
}
