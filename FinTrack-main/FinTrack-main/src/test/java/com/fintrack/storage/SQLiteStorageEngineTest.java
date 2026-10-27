package com.fintrack.storage;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SQLiteStorageEngine In-Memory Integration Tests")
public class SQLiteStorageEngineTest {

    private SQLiteStorageEngine storage;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Use SQLite in-memory database instance for isolated test runs
        storage = new SQLiteStorageEngine("jdbc:sqlite::memory:");
        testUser = new User(
                "test-user-id",
                "John Doe",
                "john.doe@example.com",
                "USD",
                new BigDecimal("5000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("1000.00")
        );
        storage.saveUser(testUser);
    }

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    @DisplayName("Should successfully perform User CRUD")
    void testUserCrud() {
        Optional<User> queried = storage.getUserById(testUser.getId());
        assertTrue(queried.isPresent());
        assertEquals("John Doe", queried.get().getName());

        testUser.setName("John Updated");
        storage.updateUser(testUser);

        Optional<User> updated = storage.getUserById(testUser.getId());
        assertTrue(updated.isPresent());
        assertEquals("John Updated", updated.get().getName());
    }

    @Test
    @DisplayName("Should persist and retrieve Transactions for user")
    void testTransactionCrud() {
        Transaction tx1 = new Transaction(
                "tx-1",
                testUser.getId(),
                LocalDateTime.now().minusHours(2),
                Category.GROCERIES,
                new BigDecimal("65.20"),
                PaymentMode.CARD,
                TransactionType.EXPENSE,
                "Supermarket visit"
        );
        storage.saveTransaction(tx1);

        Optional<Transaction> queried = storage.getTransactionById("tx-1");
        assertTrue(queried.isPresent());
        assertEquals(0, new BigDecimal("65.20").compareTo(queried.get().getAmount()));
        assertEquals(Category.GROCERIES, queried.get().getCategory());

        List<Transaction> userTxList = storage.getTransactionsByUserId(testUser.getId());
        assertEquals(1, userTxList.size());
    }

    @Test
    @DisplayName("Should cascade delete transactions when user is deleted")
    void testCascadeDelete() {
        Transaction tx = new Transaction(
                "tx-cascade",
                testUser.getId(),
                LocalDateTime.now(),
                Category.UTILITIES,
                new BigDecimal("120.00"),
                PaymentMode.UPI,
                TransactionType.EXPENSE,
                "Electricity bill"
        );
        storage.saveTransaction(tx);

        assertEquals(1, storage.getTransactionsByUserId(testUser.getId()).size());

        // Deleting user must cascade-delete foreign keys in SQLite
        storage.deleteUser(testUser.getId());

        assertTrue(storage.getUserById(testUser.getId()).isEmpty());
        assertTrue(storage.getTransactionById("tx-cascade").isEmpty());
    }

    @Test
    @DisplayName("Should export and import records to CSV files")
    void testCsvExportImportCycle(@TempDir Path tempDir) {
        Transaction tx = new Transaction(
                "tx-export",
                testUser.getId(),
                LocalDateTime.now(),
                Category.SALARY,
                new BigDecimal("4500.00"),
                PaymentMode.BANK_TRANSFER,
                TransactionType.INCOME,
                "Monthly Salary"
        );
        storage.saveTransaction(tx);

        Budget budget = new Budget("b-1", testUser.getId(), Category.FOOD_AND_DINING, new BigDecimal("400.00"), 10, 2026);
        storage.saveBudget(budget);

        Goal goal = new Goal("g-1", testUser.getId(), "Car Fund", new BigDecimal("15000.00"), new BigDecimal("3000.00"), LocalDate.of(2027, 1, 1));
        storage.saveGoal(goal);

        String exportDir = tempDir.resolve("exported_data").toString();
        storage.exportToCsv(exportDir);

        // Open a secondary fresh in-memory database and import
        try (SQLiteStorageEngine secondaryEngine = new SQLiteStorageEngine("jdbc:sqlite::memory:")) {
            secondaryEngine.importFromCsv(exportDir);

            assertTrue(secondaryEngine.getUserById(testUser.getId()).isPresent());
            assertTrue(secondaryEngine.getTransactionById("tx-export").isPresent());
            assertTrue(secondaryEngine.getBudgetById("b-1").isPresent());
            assertTrue(secondaryEngine.getGoalById("g-1").isPresent());
        }
    }
}
