package com.fintrack.storage;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.ExchangeRate;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.util.CsvUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed implementation of {@link StorageEngine} utilizing JDBC PreparedStatements,
 * connection pooling hooks, and foreign key integrity.
 */
public class SQLiteStorageEngine implements StorageEngine {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DEFAULT_DB_PATH = "fintrack.db";

    private final String jdbcUrl;
    private Connection connection;

    /**
     * Constructs a SQLiteStorageEngine targeting the default local database file ("fintrack.db").
     */
    public SQLiteStorageEngine() {
        this(DEFAULT_DB_PATH);
    }

    /**
     * Constructs a SQLiteStorageEngine targeting a specific database path or URI
     * (e.g., ":memory:" for transient unit testing).
     *
     * @param dbPath path to SQLite database file or in-memory specifier
     */
    public SQLiteStorageEngine(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            this.jdbcUrl = "jdbc:sqlite:" + DEFAULT_DB_PATH;
        } else if (dbPath.startsWith("jdbc:sqlite:")) {
            this.jdbcUrl = dbPath;
        } else {
            this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        }
        initialize();
    }

    /**
     * Establishes database connection and ensures all relational tables and indexes exist.
     */
    @Override
    public synchronized void initialize() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(jdbcUrl);
            }
            try (Statement stmt = connection.createStatement()) {
                // Enable SQLite Foreign Key enforcement
                stmt.execute("PRAGMA foreign_keys = ON;");

                // 1. Users Table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL UNIQUE,
                        currency TEXT NOT NULL DEFAULT 'USD',
                        reporting_currency TEXT NOT NULL DEFAULT 'USD',
                        monthly_income NUMERIC NOT NULL DEFAULT 0.0 CHECK (monthly_income >= 0),
                        global_budget NUMERIC NOT NULL DEFAULT 0.0 CHECK (global_budget >= 0),
                        savings_goal NUMERIC NOT NULL DEFAULT 0.0 CHECK (savings_goal >= 0),
                        created_at TEXT NOT NULL DEFAULT (datetime('now'))
                    );
                """);

                // 2. Transactions Table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        timestamp TEXT NOT NULL,
                        category TEXT NOT NULL,
                        amount NUMERIC NOT NULL CHECK (amount > 0),
                        currency TEXT NOT NULL DEFAULT 'USD',
                        payment_mode TEXT NOT NULL,
                        type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
                        note TEXT,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    );
                """);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_user_timestamp ON transactions(user_id, timestamp);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category);");

                // 3. Budgets Table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        category TEXT NOT NULL,
                        monthly_limit NUMERIC NOT NULL CHECK (monthly_limit > 0),
                        currency TEXT NOT NULL DEFAULT 'USD',
                        month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
                        year INTEGER NOT NULL CHECK (year >= 2000),
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        UNIQUE (user_id, category, month, year)
                    );
                """);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_budgets_user_period ON budgets(user_id, year, month);");

                // 4. Goals Table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS goals (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        target_amount NUMERIC NOT NULL CHECK (target_amount > 0),
                        current_amount NUMERIC NOT NULL DEFAULT 0.0 CHECK (current_amount >= 0),
                        currency TEXT NOT NULL DEFAULT 'USD',
                        target_date TEXT NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    );
                """);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_user ON goals(user_id);");

                // 5. Exchange Rates Cache Table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS exchange_rates (
                        base_currency TEXT NOT NULL,
                        target_currency TEXT NOT NULL,
                        rate NUMERIC NOT NULL CHECK (rate > 0),
                        retrieval_timestamp TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        PRIMARY KEY (base_currency, target_currency)
                    );
                """);

                // Idempotent column migrations for existing databases
                runMigrations(connection, stmt);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to initialize SQLite database schema: " + e.getMessage(), e);
        }
    }

    private void runMigrations(Connection conn, Statement stmt) throws SQLException {
        if (!columnExists(conn, "transactions", "currency")) {
            stmt.execute("ALTER TABLE transactions ADD COLUMN currency TEXT;");
            stmt.execute("UPDATE transactions SET currency = (SELECT currency FROM users WHERE users.id = transactions.user_id) WHERE currency IS NULL;");
            stmt.execute("UPDATE transactions SET currency = 'USD' WHERE currency IS NULL;");
        }
        if (!columnExists(conn, "budgets", "currency")) {
            stmt.execute("ALTER TABLE budgets ADD COLUMN currency TEXT;");
            stmt.execute("UPDATE budgets SET currency = (SELECT currency FROM users WHERE users.id = budgets.user_id) WHERE currency IS NULL;");
            stmt.execute("UPDATE budgets SET currency = 'USD' WHERE currency IS NULL;");
        }
        if (!columnExists(conn, "goals", "currency")) {
            stmt.execute("ALTER TABLE goals ADD COLUMN currency TEXT;");
            stmt.execute("UPDATE goals SET currency = (SELECT currency FROM users WHERE users.id = goals.user_id) WHERE currency IS NULL;");
            stmt.execute("UPDATE goals SET currency = 'USD' WHERE currency IS NULL;");
        }
        if (!columnExists(conn, "users", "reporting_currency")) {
            stmt.execute("ALTER TABLE users ADD COLUMN reporting_currency TEXT;");
            stmt.execute("UPDATE users SET reporting_currency = currency WHERE reporting_currency IS NULL;");
            stmt.execute("UPDATE users SET reporting_currency = 'USD' WHERE reporting_currency IS NULL;");
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ");")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
        }
        return connection;
    }

    // =========================================================================
    // User CRUD
    // =========================================================================

    @Override
    public void saveUser(User user) {
        String sql = """
            INSERT INTO users (id, name, email, currency, reporting_currency, monthly_income, global_budget, savings_goal)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getCurrency());
            ps.setString(5, user.getReportingCurrency());
            ps.setBigDecimal(6, user.getMonthlyIncome());
            ps.setBigDecimal(7, user.getGlobalBudget());
            ps.setBigDecimal(8, user.getSavingsGoal());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to insert user [" + user.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> getUserById(String id) {
        String sql = "SELECT id, name, email, currency, reporting_currency, monthly_income, global_budget, savings_goal FROM users WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query user by id [" + id + "]: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> getUserByEmail(String email) {
        String sql = "SELECT id, name, email, currency, reporting_currency, monthly_income, global_budget, savings_goal FROM users WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query user by email [" + email + "]: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> getAllUsers() {
        String sql = "SELECT id, name, email, currency, reporting_currency, monthly_income, global_budget, savings_goal FROM users ORDER BY name ASC";
        List<User> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query all users: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateUser(User user) {
        String sql = """
            UPDATE users SET name = ?, email = ?, currency = ?, reporting_currency = ?, monthly_income = ?, global_budget = ?, savings_goal = ?
            WHERE id = ?
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getCurrency());
            ps.setString(4, user.getReportingCurrency());
            ps.setBigDecimal(5, user.getMonthlyIncome());
            ps.setBigDecimal(6, user.getGlobalBudget());
            ps.setBigDecimal(7, user.getSavingsGoal());
            ps.setString(8, user.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new StorageException("User with ID [" + user.getId() + "] does not exist to update.");
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update user [" + user.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteUser(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete user [" + id + "]: " + e.getMessage(), e);
        }
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User u = new User(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("currency"),
                rs.getBigDecimal("monthly_income"),
                rs.getBigDecimal("global_budget"),
                rs.getBigDecimal("savings_goal")
        );
        try {
            String rep = rs.getString("reporting_currency");
            if (rep != null && !rep.isBlank()) {
                u.setReportingCurrency(rep);
            }
        } catch (SQLException ignored) {}
        return u;
    }

    // =========================================================================
    // Transaction CRUD
    // =========================================================================

    @Override
    public void saveTransaction(Transaction transaction) {
        String sql = """
            INSERT INTO transactions (id, user_id, timestamp, category, amount, currency, payment_mode, type, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, transaction.getId());
            ps.setString(2, transaction.getUserId());
            ps.setString(3, transaction.getTimestamp().format(ISO_FORMATTER));
            ps.setString(4, transaction.getCategory().name());
            ps.setBigDecimal(5, transaction.getAmount());
            ps.setString(6, transaction.getCurrency());
            ps.setString(7, transaction.getPaymentMode().name());
            ps.setString(8, transaction.getType().name());
            ps.setString(9, transaction.getNote());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to insert transaction [" + transaction.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Transaction> getTransactionById(String id) {
        String sql = "SELECT id, user_id, timestamp, category, amount, currency, payment_mode, type, note FROM transactions WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query transaction by id [" + id + "]: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Transaction> getAllTransactions() {
        String sql = "SELECT id, user_id, timestamp, category, amount, currency, payment_mode, type, note FROM transactions ORDER BY timestamp DESC";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToTransaction(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query all transactions: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Transaction> getTransactionsByUserId(String userId) {
        String sql = "SELECT id, user_id, timestamp, category, amount, currency, payment_mode, type, note FROM transactions WHERE user_id = ? ORDER BY timestamp DESC";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query transactions for user [" + userId + "]: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Transaction> getTransactionsByDateRange(String userId, LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT id, user_id, timestamp, category, amount, currency, payment_mode, type, note
            FROM transactions
            WHERE user_id = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp DESC
        """;
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, start.format(ISO_FORMATTER));
            ps.setString(3, end.format(ISO_FORMATTER));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTransaction(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query transactions by range: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateTransaction(Transaction transaction) {
        String sql = """
            UPDATE transactions
            SET user_id = ?, timestamp = ?, category = ?, amount = ?, currency = ?, payment_mode = ?, type = ?, note = ?
            WHERE id = ?
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, transaction.getUserId());
            ps.setString(2, transaction.getTimestamp().format(ISO_FORMATTER));
            ps.setString(3, transaction.getCategory().name());
            ps.setBigDecimal(4, transaction.getAmount());
            ps.setString(5, transaction.getCurrency());
            ps.setString(6, transaction.getPaymentMode().name());
            ps.setString(7, transaction.getType().name());
            ps.setString(8, transaction.getNote());
            ps.setString(9, transaction.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new StorageException("Transaction with ID [" + transaction.getId() + "] does not exist to update.");
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update transaction [" + transaction.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteTransaction(String id) {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete transaction [" + id + "]: " + e.getMessage(), e);
        }
    }

    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        String currency = "USD";
        try {
            String c = rs.getString("currency");
            if (c != null && !c.isBlank()) currency = c;
        } catch (SQLException ignored) {}
        return new Transaction(
                rs.getString("id"),
                rs.getString("user_id"),
                LocalDateTime.parse(rs.getString("timestamp"), ISO_FORMATTER),
                Category.fromString(rs.getString("category")),
                rs.getBigDecimal("amount"),
                currency,
                PaymentMode.fromString(rs.getString("payment_mode")),
                TransactionType.fromString(rs.getString("type")),
                rs.getString("note")
        );
    }

    // =========================================================================
    // Budget CRUD
    // =========================================================================

    @Override
    public void saveBudget(Budget budget) {
        String sql = """
            INSERT INTO budgets (id, user_id, category, monthly_limit, currency, month, year)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, budget.getId());
            ps.setString(2, budget.getUserId());
            ps.setString(3, budget.getCategory().name());
            ps.setBigDecimal(4, budget.getMonthlyLimit());
            ps.setString(5, budget.getCurrency());
            ps.setInt(6, budget.getMonth());
            ps.setInt(7, budget.getYear());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to insert budget [" + budget.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Budget> getBudgetById(String id) {
        String sql = "SELECT id, user_id, category, monthly_limit, currency, month, year FROM budgets WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBudget(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query budget by id [" + id + "]: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Budget> getBudgetsByUserId(String userId) {
        String sql = "SELECT id, user_id, category, monthly_limit, currency, month, year FROM budgets WHERE user_id = ? ORDER BY year DESC, month DESC";
        List<Budget> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBudget(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query budgets for user [" + userId + "]: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public Optional<Budget> getBudgetByUserAndCategory(String userId, Category category, int month, int year) {
        String sql = """
            SELECT id, user_id, category, monthly_limit, currency, month, year
            FROM budgets
            WHERE user_id = ? AND category = ? AND month = ? AND year = ?
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, category.name());
            ps.setInt(3, month);
            ps.setInt(4, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBudget(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query category budget: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void updateBudget(Budget budget) {
        String sql = """
            UPDATE budgets
            SET category = ?, monthly_limit = ?, currency = ?, month = ?, year = ?
            WHERE id = ?
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, budget.getCategory().name());
            ps.setBigDecimal(2, budget.getMonthlyLimit());
            ps.setString(3, budget.getCurrency());
            ps.setInt(4, budget.getMonth());
            ps.setInt(5, budget.getYear());
            ps.setString(6, budget.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new StorageException("Budget with ID [" + budget.getId() + "] does not exist to update.");
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update budget [" + budget.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteBudget(String id) {
        String sql = "DELETE FROM budgets WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete budget [" + id + "]: " + e.getMessage(), e);
        }
    }

    private Budget mapResultSetToBudget(ResultSet rs) throws SQLException {
        String currency = "USD";
        try {
            String c = rs.getString("currency");
            if (c != null && !c.isBlank()) currency = c;
        } catch (SQLException ignored) {}
        return new Budget(
                rs.getString("id"),
                rs.getString("user_id"),
                Category.fromString(rs.getString("category")),
                rs.getBigDecimal("monthly_limit"),
                currency,
                rs.getInt("month"),
                rs.getInt("year")
        );
    }

    // =========================================================================
    // Goal CRUD
    // =========================================================================

    @Override
    public void saveGoal(Goal goal) {
        String sql = """
            INSERT INTO goals (id, user_id, name, target_amount, current_amount, currency, target_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, goal.getId());
            ps.setString(2, goal.getUserId());
            ps.setString(3, goal.getName());
            ps.setBigDecimal(4, goal.getTargetAmount());
            ps.setBigDecimal(5, goal.getCurrentAmount());
            ps.setString(6, goal.getCurrency());
            ps.setString(7, goal.getTargetDate().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to insert goal [" + goal.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Goal> getGoalById(String id) {
        String sql = "SELECT id, user_id, name, target_amount, current_amount, currency, target_date FROM goals WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGoal(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query goal by id [" + id + "]: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Goal> getGoalsByUserId(String userId) {
        String sql = "SELECT id, user_id, name, target_amount, current_amount, currency, target_date FROM goals WHERE user_id = ? ORDER BY target_date ASC";
        List<Goal> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToGoal(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query goals for user [" + userId + "]: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void updateGoal(Goal goal) {
        String sql = """
            UPDATE goals
            SET name = ?, target_amount = ?, current_amount = ?, currency = ?, target_date = ?
            WHERE id = ?
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, goal.getName());
            ps.setBigDecimal(2, goal.getTargetAmount());
            ps.setBigDecimal(3, goal.getCurrentAmount());
            ps.setString(4, goal.getCurrency());
            ps.setString(5, goal.getTargetDate().toString());
            ps.setString(6, goal.getId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new StorageException("Goal with ID [" + goal.getId() + "] does not exist to update.");
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update goal [" + goal.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteGoal(String id) {
        String sql = "DELETE FROM goals WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete goal [" + id + "]: " + e.getMessage(), e);
        }
    }

    private Goal mapResultSetToGoal(ResultSet rs) throws SQLException {
        String currency = "USD";
        try {
            String c = rs.getString("currency");
            if (c != null && !c.isBlank()) currency = c;
        } catch (SQLException ignored) {}
        return new Goal(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("name"),
                rs.getBigDecimal("target_amount"),
                rs.getBigDecimal("current_amount"),
                currency,
                LocalDate.parse(rs.getString("target_date"))
        );
    }

    // =========================================================================
    // Exchange Rate Cache
    // =========================================================================

    @Override
    public void saveExchangeRate(ExchangeRate rate) {
        String sql = """
            INSERT INTO exchange_rates (base_currency, target_currency, rate, retrieval_timestamp, provider)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(base_currency, target_currency) DO UPDATE SET
                rate = excluded.rate,
                retrieval_timestamp = excluded.retrieval_timestamp,
                provider = excluded.provider
        """;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, rate.getBaseCurrency().toUpperCase());
            ps.setString(2, rate.getTargetCurrency().toUpperCase());
            ps.setBigDecimal(3, rate.getRate());
            ps.setString(4, rate.getRetrievalTimestamp().format(ISO_FORMATTER));
            ps.setString(5, rate.getProvider() != null ? rate.getProvider() : "UNKNOWN");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save exchange rate: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ExchangeRate> getExchangeRate(String baseCurrency, String targetCurrency) {
        String sql = "SELECT base_currency, target_currency, rate, retrieval_timestamp, provider FROM exchange_rates WHERE base_currency = ? AND target_currency = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, baseCurrency.toUpperCase());
            ps.setString(2, targetCurrency.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToExchangeRate(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query exchange rate: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<ExchangeRate> getExchangeRatesByBase(String baseCurrency) {
        String sql = "SELECT base_currency, target_currency, rate, retrieval_timestamp, provider FROM exchange_rates WHERE base_currency = ?";
        List<ExchangeRate> list = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, baseCurrency.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToExchangeRate(rs));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to query exchange rates by base: " + e.getMessage(), e);
        }
        return list;
    }

    private ExchangeRate mapResultSetToExchangeRate(ResultSet rs) throws SQLException {
        return new ExchangeRate(
                rs.getString("base_currency"),
                rs.getString("target_currency"),
                rs.getBigDecimal("rate"),
                LocalDateTime.parse(rs.getString("retrieval_timestamp"), ISO_FORMATTER),
                rs.getString("provider")
        );
    }

    // =========================================================================
    // Data Interchange (CSV Export & Import)
    // =========================================================================

    @Override
    public void exportToCsv(String targetDirectoryPath) {
        try {
            Path targetDir = Paths.get(targetDirectoryPath);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Export Users
            // Export Users
            File usersCsv = targetDir.resolve("users.csv").toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(usersCsv))) {
                writer.write("id,name,email,currency,reporting_currency,monthly_income,global_budget,savings_goal\n");
                for (User u : getAllUsers()) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s\n",
                            CsvUtil.escapeField(u.getId()), CsvUtil.escapeField(u.getName()), CsvUtil.escapeField(u.getEmail()),
                            CsvUtil.escapeField(u.getCurrency()), CsvUtil.escapeField(u.getReportingCurrency()),
                            u.getMonthlyIncome(), u.getGlobalBudget(), u.getSavingsGoal()));
                }
            }

            // Export Transactions
            File txCsv = targetDir.resolve("transactions.csv").toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(txCsv))) {
                writer.write("id,user_id,timestamp,category,amount,currency,payment_mode,type,note\n");
                for (Transaction t : getAllTransactions()) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            CsvUtil.escapeField(t.getId()), CsvUtil.escapeField(t.getUserId()), t.getTimestamp().format(ISO_FORMATTER),
                            t.getCategory().name(), t.getAmount(), CsvUtil.escapeField(t.getCurrency()), t.getPaymentMode().name(),
                            t.getType().name(), CsvUtil.escapeField(t.getNote())));
                }
            }

            // Export Budgets
            File budgetCsv = targetDir.resolve("budgets.csv").toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(budgetCsv))) {
                writer.write("id,user_id,category,monthly_limit,currency,month,year\n");
                for (User u : getAllUsers()) {
                    for (Budget b : getBudgetsByUserId(u.getId())) {
                        writer.write(String.format("\"%s\",\"%s\",\"%s\",%s,\"%s\",%d,%d\n",
                                CsvUtil.escapeField(b.getId()), CsvUtil.escapeField(b.getUserId()), b.getCategory().name(),
                                b.getMonthlyLimit(), CsvUtil.escapeField(b.getCurrency()), b.getMonth(), b.getYear()));
                    }
                }
            }

            // Export Goals
            File goalCsv = targetDir.resolve("goals.csv").toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(goalCsv))) {
                writer.write("id,user_id,name,target_amount,current_amount,currency,target_date\n");
                for (User u : getAllUsers()) {
                    for (Goal g : getGoalsByUserId(u.getId())) {
                        writer.write(String.format("\"%s\",\"%s\",\"%s\",%s,%s,\"%s\",\"%s\"\n",
                                CsvUtil.escapeField(g.getId()), CsvUtil.escapeField(g.getUserId()), CsvUtil.escapeField(g.getName()),
                                g.getTargetAmount(), g.getCurrentAmount(), CsvUtil.escapeField(g.getCurrency()), g.getTargetDate()));
                    }
                }
            }
        } catch (IOException e) {
            throw new StorageException("Failed to export database tables to CSV directory: " + e.getMessage(), e);
        }
    }

    @Override
    public void importFromCsv(String sourceDirectoryPath) {
        Path sourceDir = Paths.get(sourceDirectoryPath);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new StorageException("Source directory does not exist: " + sourceDirectoryPath);
        }

        try {
            Connection conn = getConnection();
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Import Users
                Path usersFile = sourceDir.resolve("users.csv");
                if (Files.exists(usersFile)) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(usersFile.toFile()))) {
                        List<String[]> records = CsvUtil.parseAll(reader);
                        for (int i = 1; i < records.size(); i++) {
                            String[] cols = records.get(i);
                            if (cols.length >= 8) {
                                User u = new User(cols[0], cols[1], cols[2], cols[3],
                                        new BigDecimal(cols[5]), new BigDecimal(cols[6]), new BigDecimal(cols[7]));
                                u.setReportingCurrency(cols[4]);
                                if (getUserById(u.getId()).isPresent()) {
                                    updateUser(u);
                                } else {
                                    saveUser(u);
                                }
                            } else if (cols.length >= 7) {
                                User u = new User(cols[0], cols[1], cols[2], cols[3],
                                        new BigDecimal(cols[4]), new BigDecimal(cols[5]), new BigDecimal(cols[6]));
                                if (getUserById(u.getId()).isPresent()) {
                                    updateUser(u);
                                } else {
                                    saveUser(u);
                                }
                            }
                        }
                    }
                }

                // Import Transactions
                Path txFile = sourceDir.resolve("transactions.csv");
                if (Files.exists(txFile)) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(txFile.toFile()))) {
                        List<String[]> records = CsvUtil.parseAll(reader);
                        for (int i = 1; i < records.size(); i++) {
                            String[] cols = records.get(i);
                            if (cols.length >= 9) {
                                Transaction t = new Transaction(
                                        cols[0],
                                        cols[1],
                                        LocalDateTime.parse(cols[2], ISO_FORMATTER),
                                        Category.fromString(cols[3]),
                                        new BigDecimal(cols[4]),
                                        cols[5],
                                        PaymentMode.fromString(cols[6]),
                                        TransactionType.fromString(cols[7]),
                                        cols[8]
                                );
                                if (getTransactionById(t.getId()).isPresent()) {
                                    updateTransaction(t);
                                } else {
                                    saveTransaction(t);
                                }
                            } else if (cols.length >= 8) {
                                String userCurrency = getUserById(cols[1]).map(User::getCurrency).orElse("USD");
                                Transaction t = new Transaction(
                                        cols[0],
                                        cols[1],
                                        LocalDateTime.parse(cols[2], ISO_FORMATTER),
                                        Category.fromString(cols[3]),
                                        new BigDecimal(cols[4]),
                                        userCurrency,
                                        PaymentMode.fromString(cols[5]),
                                        TransactionType.fromString(cols[6]),
                                        cols[7]
                                );
                                if (getTransactionById(t.getId()).isPresent()) {
                                    updateTransaction(t);
                                } else {
                                    saveTransaction(t);
                                }
                            }
                        }
                    }
                }

                // Import Budgets
                Path budgetFile = sourceDir.resolve("budgets.csv");
                if (Files.exists(budgetFile)) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(budgetFile.toFile()))) {
                        List<String[]> records = CsvUtil.parseAll(reader);
                        for (int i = 1; i < records.size(); i++) {
                            String[] cols = records.get(i);
                            if (cols.length >= 7) {
                                Budget b = new Budget(
                                        cols[0],
                                        cols[1],
                                        Category.fromString(cols[2]),
                                        new BigDecimal(cols[3]),
                                        cols[4],
                                        Integer.parseInt(cols[5]),
                                        Integer.parseInt(cols[6])
                                );
                                if (getBudgetById(b.getId()).isPresent()) {
                                    updateBudget(b);
                                } else {
                                    saveBudget(b);
                                }
                            } else if (cols.length >= 6) {
                                String userCurrency = getUserById(cols[1]).map(User::getCurrency).orElse("USD");
                                Budget b = new Budget(
                                        cols[0],
                                        cols[1],
                                        Category.fromString(cols[2]),
                                        new BigDecimal(cols[3]),
                                        userCurrency,
                                        Integer.parseInt(cols[4]),
                                        Integer.parseInt(cols[5])
                                );
                                if (getBudgetById(b.getId()).isPresent()) {
                                    updateBudget(b);
                                } else {
                                    saveBudget(b);
                                }
                            }
                        }
                    }
                }

                // Import Goals
                Path goalFile = sourceDir.resolve("goals.csv");
                if (Files.exists(goalFile)) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(goalFile.toFile()))) {
                        List<String[]> records = CsvUtil.parseAll(reader);
                        for (int i = 1; i < records.size(); i++) {
                            String[] cols = records.get(i);
                            if (cols.length >= 7) {
                                Goal g = new Goal(
                                        cols[0],
                                        cols[1],
                                        cols[2],
                                        new BigDecimal(cols[3]),
                                        new BigDecimal(cols[4]),
                                        cols[5],
                                        LocalDate.parse(cols[6])
                                );
                                if (getGoalById(g.getId()).isPresent()) {
                                    updateGoal(g);
                                } else {
                                    saveGoal(g);
                                }
                            } else if (cols.length >= 6) {
                                String userCurrency = getUserById(cols[1]).map(User::getCurrency).orElse("USD");
                                Goal g = new Goal(
                                        cols[0],
                                        cols[1],
                                        cols[2],
                                        new BigDecimal(cols[3]),
                                        new BigDecimal(cols[4]),
                                        userCurrency,
                                        LocalDate.parse(cols[5])
                                );
                                if (getGoalById(g.getId()).isPresent()) {
                                    updateGoal(g);
                                } else {
                                    saveGoal(g);
                                }
                            }
                        }
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new StorageException("Transaction rolled back during CSV import: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new StorageException("Database error during CSV import: " + e.getMessage(), e);
        }
    }

    private String escapeCsv(String input) {
        return CsvUtil.escapeField(input);
    }

    private String[] parseCsvLine(String line) {
        return CsvUtil.parseRecord(line);
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to close SQLite connection: " + e.getMessage(), e);
        }
    }
}
