package com.fintrack.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Models & Invariant Validation Tests")
public class ModelValidationTest {

    @Test
    @DisplayName("Should successfully construct and validate a legitimate Transaction")
    void testValidTransactionCreation() {
        Transaction tx = new Transaction(
                Category.FOOD_AND_DINING,
                new BigDecimal("45.50"),
                PaymentMode.CARD,
                TransactionType.EXPENSE,
                "Dinner with friends"
        );

        assertNotNull(tx.getId());
        assertNotNull(tx.getTimestamp());
        assertEquals("default_user", tx.getUserId());
        assertEquals(Category.FOOD_AND_DINING, tx.getCategory());
        assertEquals(new BigDecimal("45.50"), tx.getAmount());
        assertEquals(PaymentMode.CARD, tx.getPaymentMode());
        assertEquals(TransactionType.EXPENSE, tx.getType());
        assertEquals("Dinner with friends", tx.getNote());
    }

    @Test
    @DisplayName("Should reject Transaction with zero or negative amounts")
    void testTransactionAmountValidation() {
        assertThrows(ValidationException.class, () -> new Transaction(
                Category.GROCERIES,
                BigDecimal.ZERO,
                PaymentMode.CASH,
                TransactionType.EXPENSE,
                "Zero amount"
        ));

        assertThrows(ValidationException.class, () -> new Transaction(
                Category.GROCERIES,
                new BigDecimal("-15.00"),
                PaymentMode.CASH,
                TransactionType.EXPENSE,
                "Negative amount"
        ));
    }

    @Test
    @DisplayName("Should reject Transaction with null required fields")
    void testTransactionNullFields() {
        assertThrows(ValidationException.class, () -> new Transaction(
                null,
                new BigDecimal("100.00"),
                PaymentMode.UPI,
                TransactionType.INCOME,
                "Null Category"
        ));

        assertThrows(ValidationException.class, () -> new Transaction(
                Category.SALARY,
                new BigDecimal("100.00"),
                null,
                TransactionType.INCOME,
                "Null PaymentMode"
        ));
    }

    @Test
    @DisplayName("Should sanitize null note into an empty string")
    void testTransactionNoteSanitization() {
        Transaction tx = new Transaction(
                Category.SALARY,
                new BigDecimal("5000.00"),
                PaymentMode.BANK_TRANSFER,
                TransactionType.INCOME,
                null
        );
        assertEquals("", tx.getNote());
    }

    @Test
    @DisplayName("Should validate User email and negative budget limits")
    void testUserValidation() {
        User validUser = new User(
                "user-1",
                "Alex Hunter",
                "alex@fintrack.org",
                "USD",
                new BigDecimal("6000.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("1000.00")
        );
        assertEquals("alex@fintrack.org", validUser.getEmail());

        // Invalid email format
        assertThrows(ValidationException.class, () -> new User(
                "user-2",
                "Alex",
                "invalid-email-string",
                "USD",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));

        // Negative monthly income
        assertThrows(ValidationException.class, () -> new User(
                "user-3",
                "Alex",
                "alex@test.com",
                "USD",
                new BigDecimal("-500.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));
    }

    @Test
    @DisplayName("Should enforce Budget month boundaries (1-12) and positive limit")
    void testBudgetValidation() {
        assertDoesNotThrow(() -> new Budget("u1", Category.ENTERTAINMENT, new BigDecimal("200.00")));

        assertThrows(ValidationException.class, () -> new Budget(
                "b1", "u1", Category.ENTERTAINMENT, new BigDecimal("200.00"), 13, 2026
        ));

        assertThrows(ValidationException.class, () -> new Budget(
                "b2", "u1", Category.ENTERTAINMENT, BigDecimal.ZERO, 5, 2026
        ));
    }

    @Test
    @DisplayName("Should compute Goal progress percentage accurately")
    void testGoalProgressCalculation() {
        Goal goal = new Goal(
                "g1",
                "u1",
                "Emergency Fund",
                new BigDecimal("10000.00"),
                new BigDecimal("2500.00"),
                LocalDate.now().plusMonths(6)
        );

        assertEquals(25.0, goal.getProgressPercentage(), 0.001);
    }
}
