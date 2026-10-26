package com.fintrack.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transaction Model & Invariant Validation Tests")
public class TransactionValidationTest {

    @Test
    @DisplayName("Should create valid Transaction when all fields satisfy constraints")
    void testValidTransaction() {
        LocalDateTime now = LocalDateTime.now();
        Transaction tx = new Transaction(
                "tx-100",
                "user-1",
                now,
                Category.GROCERIES,
                new BigDecimal("1250.75"),
                PaymentMode.UPI,
                TransactionType.EXPENSE,
                "Weekly organic groceries"
        );

        assertEquals("tx-100", tx.getId());
        assertEquals("user-1", tx.getUserId());
        assertEquals(now, tx.getTimestamp());
        assertEquals(Category.GROCERIES, tx.getCategory());
        assertEquals(new BigDecimal("1250.75"), tx.getAmount());
        assertEquals(PaymentMode.UPI, tx.getPaymentMode());
        assertEquals(TransactionType.EXPENSE, tx.getType());
        assertEquals("Weekly organic groceries", tx.getNote());
    }

    @Test
    @DisplayName("Should auto-generate ID and timestamp in convenience constructor")
    void testConvenienceConstructorDefaults() {
        Transaction tx = new Transaction(
                Category.SALARY,
                new BigDecimal("85000.00"),
                PaymentMode.BANK_TRANSFER,
                TransactionType.INCOME,
                "Monthly Salary"
        );

        assertNotNull(tx.getId());
        assertFalse(tx.getId().isBlank());
        assertNotNull(tx.getTimestamp());
        assertEquals("default_user", tx.getUserId());
        assertEquals(new BigDecimal("85000.00"), tx.getAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-150.00", "-99999.99"})
    @DisplayName("Should reject non-positive amounts (amount <= 0)")
    void testNonPositiveAmounts(String invalidAmount) {
        ValidationException ex = assertThrows(ValidationException.class, () -> new Transaction(
                Category.FOOD_AND_DINING,
                new BigDecimal(invalidAmount),
                PaymentMode.CARD,
                TransactionType.EXPENSE,
                "Invalid"
        ));
        assertTrue(ex.getMessage().contains("strictly greater than 0"));
    }

    @Test
    @DisplayName("Should throw ValidationException when amount is null")
    void testNullAmount() {
        ValidationException ex = assertThrows(ValidationException.class, () -> new Transaction(
                Category.FOOD_AND_DINING,
                null,
                PaymentMode.CARD,
                TransactionType.EXPENSE,
                "Note"
        ));
        assertTrue(ex.getMessage().contains("amount cannot be null"));
    }

    @Test
    @DisplayName("Should throw ValidationException when required fields are null")
    void testNullRequiredFields() {
        BigDecimal validAmt = new BigDecimal("500.00");

        assertThrows(ValidationException.class, () -> new Transaction(null, validAmt, PaymentMode.UPI, TransactionType.EXPENSE, ""));
        assertThrows(ValidationException.class, () -> new Transaction(Category.FOOD_AND_DINING, validAmt, null, TransactionType.EXPENSE, ""));
        assertThrows(ValidationException.class, () -> new Transaction(Category.FOOD_AND_DINING, validAmt, PaymentMode.UPI, null, ""));
        assertThrows(ValidationException.class, () -> new Transaction(null, Category.FOOD_AND_DINING, validAmt, PaymentMode.UPI, TransactionType.EXPENSE, ""));
    }

    @Test
    @DisplayName("Should sanitize null note into empty string")
    void testNoteSanitization() {
        Transaction tx = new Transaction(Category.UTILITIES, new BigDecimal("1200.00"), PaymentMode.UPI, TransactionType.EXPENSE, null);
        assertNotNull(tx.getNote());
        assertEquals("", tx.getNote());

        tx.setNote("  Trimmed Note  ");
        assertEquals("Trimmed Note", tx.getNote());

        tx.setNote(null);
        assertEquals("", tx.getNote());
    }

    @Test
    @DisplayName("Should enforce entity equality based on unique identifier")
    void testEqualityAndHashCode() {
        LocalDateTime now = LocalDateTime.now();
        Transaction tx1 = new Transaction("tx-identical", "u1", now, Category.SHOPPING, new BigDecimal("100.00"), PaymentMode.CARD, TransactionType.EXPENSE, "A");
        Transaction tx2 = new Transaction("tx-identical", "u2", now, Category.TRAVEL, new BigDecimal("500.00"), PaymentMode.CASH, TransactionType.INCOME, "B");
        Transaction tx3 = new Transaction("tx-different", "u1", now, Category.SHOPPING, new BigDecimal("100.00"), PaymentMode.CARD, TransactionType.EXPENSE, "A");

        assertEquals(tx1, tx2);
        assertEquals(tx1.hashCode(), tx2.hashCode());
        assertNotEquals(tx1, tx3);
    }

    @Test
    @DisplayName("Should resolve enums case-insensitively")
    void testEnumStringParsing() {
        assertEquals(Category.FOOD_AND_DINING, Category.fromString("Food & Dining"));
        assertEquals(Category.FOOD_AND_DINING, Category.fromString("food_and_dining"));
        assertEquals(PaymentMode.BANK_TRANSFER, PaymentMode.fromString("Bank Transfer"));
        assertEquals(PaymentMode.BANK_TRANSFER, PaymentMode.fromString("BANK_TRANSFER"));
        assertEquals(PaymentMode.UPI, PaymentMode.fromString("upi"));
        assertEquals(TransactionType.INCOME, TransactionType.fromString("income"));
        assertEquals(TransactionType.EXPENSE, TransactionType.fromString("EXPENSE"));

        assertThrows(ValidationException.class, () -> Category.fromString("NonExistentCategory"));
        assertThrows(ValidationException.class, () -> PaymentMode.fromString("CryptoBitcoin"));
        assertThrows(ValidationException.class, () -> TransactionType.fromString("LoanTransfer"));
    }
}
