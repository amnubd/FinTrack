package com.fintrack.storage;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsvStorageEngine Flat-File Persistence Tests")
public class CsvStorageEngineTest {

    @Test
    @DisplayName("Should initialize CSV files, write records, and reload on restart")
    void testCsvStorageLifecycle(@TempDir Path tempDir) {
        String dataDir = tempDir.resolve("csv_test_data").toString();

        User user = new User("csv-u1", "Sara Connor", "sara@fintrack.io", "EUR",
                new BigDecimal("4200.00"), new BigDecimal("1800.00"), new BigDecimal("500.00"));

        Transaction tx = new Transaction("csv-tx1", "csv-u1", LocalDateTime.now(),
                Category.TRANSPORTATION, new BigDecimal("45.00"), PaymentMode.CARD,
                TransactionType.EXPENSE, "Metro pass");

        Budget budget = new Budget("csv-b1", "csv-u1", Category.TRANSPORTATION, new BigDecimal("100.00"), 10, 2026);
        Goal goal = new Goal("csv-g1", "csv-u1", "Emergency Reserve", new BigDecimal("5000.00"), new BigDecimal("1200.00"), LocalDate.of(2027, 6, 1));

        // 1. First engine session: save data
        try (CsvStorageEngine engine = new CsvStorageEngine(dataDir)) {
            engine.saveUser(user);
            engine.saveTransaction(tx);
            engine.saveBudget(budget);
            engine.saveGoal(goal);

            // Verify files exist on filesystem
            assertTrue(Files.exists(tempDir.resolve("csv_test_data/users.csv")));
            assertTrue(Files.exists(tempDir.resolve("csv_test_data/transactions.csv")));
            assertTrue(Files.exists(tempDir.resolve("csv_test_data/budgets.csv")));
            assertTrue(Files.exists(tempDir.resolve("csv_test_data/goals.csv")));
        }

        // 2. Second engine session targeting same folder: verify disk recovery
        try (CsvStorageEngine reloadedEngine = new CsvStorageEngine(dataDir)) {
            Optional<User> reloadedUser = reloadedEngine.getUserById("csv-u1");
            assertTrue(reloadedUser.isPresent());
            assertEquals("Sara Connor", reloadedUser.get().getName());
            assertEquals("EUR", reloadedUser.get().getCurrency());

            Optional<Transaction> reloadedTx = reloadedEngine.getTransactionById("csv-tx1");
            assertTrue(reloadedTx.isPresent());
            assertEquals(new BigDecimal("45.00"), reloadedTx.get().getAmount());
            assertEquals(Category.TRANSPORTATION, reloadedTx.get().getCategory());

            assertEquals(1, reloadedEngine.getBudgetsByUserId("csv-u1").size());
            assertEquals(1, reloadedEngine.getGoalsByUserId("csv-u1").size());
        }
    }
}
