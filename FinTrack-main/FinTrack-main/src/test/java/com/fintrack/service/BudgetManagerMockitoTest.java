package com.fintrack.service;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.storage.StorageEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetManager Mockito Isolated Unit Tests")
public class BudgetManagerMockitoTest {

    @Mock
    private StorageEngine mockStorage;

    @InjectMocks
    private BudgetManager budgetManager;

    private final String userId = "mock-user-1";

    @Test
    @DisplayName("Should evaluate BudgetStatus.OK when spend is strictly under 75% limit")
    void testStatusOk() {
        Budget budget = new Budget("b-1", userId, Category.GROCERIES, new BigDecimal("10000.00"), 10, 2026);
        when(mockStorage.getBudgetByUserAndCategory(eq(userId), eq(Category.GROCERIES), eq(10), eq(2026)))
                .thenReturn(Optional.of(budget));

        // Mock 6,000 spend (60% of 10,000 limit -> OK)
        Transaction t1 = new Transaction("t1", userId, LocalDateTime.of(2026, 10, 5, 10, 0),
                Category.GROCERIES, new BigDecimal("6000.00"), PaymentMode.UPI, TransactionType.EXPENSE, "Market");

        when(mockStorage.getTransactionsByDateRange(eq(userId), any(), any()))
                .thenReturn(List.of(t1));

        BudgetStatus status = budgetManager.getBudgetStatus(userId, Category.GROCERIES, 10, 2026);
        assertEquals(BudgetStatus.OK, status);
    }

    @Test
    @DisplayName("Should evaluate BudgetStatus.WARNING when spend is between 75% and 100% limit")
    void testStatusWarning() {
        Budget budget = new Budget("b-1", userId, Category.FOOD_AND_DINING, new BigDecimal("4000.00"), 10, 2026);
        when(mockStorage.getBudgetByUserAndCategory(eq(userId), eq(Category.FOOD_AND_DINING), eq(10), eq(2026)))
                .thenReturn(Optional.of(budget));

        // Mock 3,200 spend (80% of 4,000 limit -> WARNING)
        Transaction t1 = new Transaction("t1", userId, LocalDateTime.of(2026, 10, 12, 19, 0),
                Category.FOOD_AND_DINING, new BigDecimal("3200.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Dining");

        when(mockStorage.getTransactionsByDateRange(eq(userId), any(), any()))
                .thenReturn(List.of(t1));

        BudgetStatus status = budgetManager.getBudgetStatus(userId, Category.FOOD_AND_DINING, 10, 2026);
        assertEquals(BudgetStatus.WARNING, status);
    }

    @Test
    @DisplayName("Should evaluate BudgetStatus.EXCEEDED when spend breaches 100% limit")
    void testStatusExceeded() {
        Budget budget = new Budget("b-1", userId, Category.ENTERTAINMENT, new BigDecimal("2000.00"), 10, 2026);
        when(mockStorage.getBudgetByUserAndCategory(eq(userId), eq(Category.ENTERTAINMENT), eq(10), eq(2026)))
                .thenReturn(Optional.of(budget));

        // Mock 2,500 spend (125% of 2,000 limit -> EXCEEDED)
        Transaction t1 = new Transaction("t1", userId, LocalDateTime.of(2026, 10, 18, 20, 0),
                Category.ENTERTAINMENT, new BigDecimal("2500.00"), PaymentMode.CARD, TransactionType.EXPENSE, "Concert");

        when(mockStorage.getTransactionsByDateRange(eq(userId), any(), any()))
                .thenReturn(List.of(t1));

        BudgetStatus status = budgetManager.getBudgetStatus(userId, Category.ENTERTAINMENT, 10, 2026);
        assertEquals(BudgetStatus.EXCEEDED, status);
    }

    @Test
    @DisplayName("Should update existing budget in-place instead of saving duplicate")
    void testUpdateBudgetInPlace() {
        Budget existing = new Budget("b-existing", userId, Category.UTILITIES, new BigDecimal("3000.00"), 10, 2026);
        when(mockStorage.getBudgetByUserAndCategory(userId, Category.UTILITIES, 10, 2026))
                .thenReturn(Optional.of(existing));

        Budget updated = budgetManager.setCategoryBudget(userId, Category.UTILITIES, new BigDecimal("4500.00"), 10, 2026);

        assertEquals(new BigDecimal("4500.00"), updated.getMonthlyLimit());
        verify(mockStorage, times(1)).updateBudget(existing);
        verify(mockStorage, never()).saveBudget(any());
    }
}
