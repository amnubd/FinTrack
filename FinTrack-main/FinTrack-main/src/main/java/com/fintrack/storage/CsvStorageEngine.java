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
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * File-backed implementation of {@link StorageEngine} storing entities in flat CSV files.
 * Provides thread-safe read/write operations and in-memory caching with write-through disk synchronization.
 */
public class CsvStorageEngine implements StorageEngine {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DEFAULT_DATA_DIR = "data";

    private final Path dataDirectory;
    private final Path usersFile;
    private final Path transactionsFile;
    private final Path budgetsFile;
    private final Path goalsFile;
    private final Path exchangeRatesFile;

    private final Map<String, User> userCache = new LinkedHashMap<>();
    private final Map<String, Transaction> transactionCache = new LinkedHashMap<>();
    private final Map<String, Budget> budgetCache = new LinkedHashMap<>();
    private final Map<String, Goal> goalCache = new LinkedHashMap<>();
    private final Map<String, ExchangeRate> exchangeRateCache = new LinkedHashMap<>();

    /**
     * Constructs a CsvStorageEngine using the default root directory ("./data").
     */
    public CsvStorageEngine() {
        this(DEFAULT_DATA_DIR);
    }

    /**
     * Constructs a CsvStorageEngine targeting a specific directory path.
     *
     * @param directoryPath directory path where CSV files reside
     */
    public CsvStorageEngine(String directoryPath) {
        this.dataDirectory = Paths.get(directoryPath != null && !directoryPath.isBlank() ? directoryPath : DEFAULT_DATA_DIR);
        this.usersFile = dataDirectory.resolve("users.csv");
        this.transactionsFile = dataDirectory.resolve("transactions.csv");
        this.budgetsFile = dataDirectory.resolve("budgets.csv");
        this.goalsFile = dataDirectory.resolve("goals.csv");
        this.exchangeRatesFile = dataDirectory.resolve("exchange_rates.csv");
        initialize();
    }

    @Override
    public synchronized void initialize() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            ensureFileWithHeader(usersFile, "id,name,email,currency,reporting_currency,monthly_income,global_budget,savings_goal");
            ensureFileWithHeader(transactionsFile, "id,user_id,timestamp,category,amount,currency,payment_mode,type,note");
            ensureFileWithHeader(budgetsFile, "id,user_id,category,monthly_limit,currency,month,year");
            ensureFileWithHeader(goalsFile, "id,user_id,name,target_amount,current_amount,currency,target_date");
            ensureFileWithHeader(exchangeRatesFile, "base_currency,target_currency,rate,retrieval_timestamp,provider");

            loadUsersFromDisk();
            loadTransactionsFromDisk();
            loadBudgetsFromDisk();
            loadGoalsFromDisk();
            loadExchangeRatesFromDisk();
        } catch (IOException e) {
            throw new StorageException("Failed to initialize CSV storage directory: " + e.getMessage(), e);
        }
    }

    private void ensureFileWithHeader(Path filePath, String header) throws IOException {
        if (!Files.exists(filePath)) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath.toFile()))) {
                bw.write(header);
                bw.newLine();
            }
        }
    }

    // =========================================================================
    // User CRUD
    // =========================================================================

    @Override
    public synchronized void saveUser(User user) {
        userCache.put(user.getId(), user);
        flushUsersToDisk();
    }

    @Override
    public synchronized Optional<User> getUserById(String id) {
        return Optional.ofNullable(userCache.get(id));
    }

    @Override
    public synchronized Optional<User> getUserByEmail(String email) {
        if (email == null) return Optional.empty();
        return userCache.values().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email.trim()))
                .findFirst();
    }

    @Override
    public synchronized List<User> getAllUsers() {
        return new ArrayList<>(userCache.values());
    }

    @Override
    public synchronized void updateUser(User user) {
        if (!userCache.containsKey(user.getId())) {
            throw new StorageException("Cannot update non-existent user with ID: " + user.getId());
        }
        userCache.put(user.getId(), user);
        flushUsersToDisk();
    }

    @Override
    public synchronized void deleteUser(String id) {
        if (userCache.remove(id) != null) {
            // Cascade delete
            transactionCache.values().removeIf(t -> t.getUserId().equals(id));
            budgetCache.values().removeIf(b -> b.getUserId().equals(id));
            goalCache.values().removeIf(g -> g.getUserId().equals(id));

            flushUsersToDisk();
            flushTransactionsToDisk();
            flushBudgetsToDisk();
            flushGoalsToDisk();
        }
    }

    // =========================================================================
    // Transaction CRUD
    // =========================================================================

    @Override
    public synchronized void saveTransaction(Transaction transaction) {
        transactionCache.put(transaction.getId(), transaction);
        flushTransactionsToDisk();
    }

    @Override
    public synchronized Optional<Transaction> getTransactionById(String id) {
        return Optional.ofNullable(transactionCache.get(id));
    }

    @Override
    public synchronized List<Transaction> getAllTransactions() {
        return transactionCache.values().stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Transaction> getTransactionsByUserId(String userId) {
        return transactionCache.values().stream()
                .filter(t -> t.getUserId().equals(userId))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Transaction> getTransactionsByDateRange(String userId, LocalDateTime start, LocalDateTime end) {
        return transactionCache.values().stream()
                .filter(t -> t.getUserId().equals(userId)
                        && !t.getTimestamp().isBefore(start)
                        && !t.getTimestamp().isAfter(end))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized void updateTransaction(Transaction transaction) {
        if (!transactionCache.containsKey(transaction.getId())) {
            throw new StorageException("Cannot update non-existent transaction with ID: " + transaction.getId());
        }
        transactionCache.put(transaction.getId(), transaction);
        flushTransactionsToDisk();
    }

    @Override
    public synchronized void deleteTransaction(String id) {
        if (transactionCache.remove(id) != null) {
            flushTransactionsToDisk();
        }
    }

    // =========================================================================
    // Budget CRUD
    // =========================================================================

    @Override
    public synchronized void saveBudget(Budget budget) {
        budgetCache.put(budget.getId(), budget);
        flushBudgetsToDisk();
    }

    @Override
    public synchronized Optional<Budget> getBudgetById(String id) {
        return Optional.ofNullable(budgetCache.get(id));
    }

    @Override
    public synchronized List<Budget> getBudgetsByUserId(String userId) {
        return budgetCache.values().stream()
                .filter(b -> b.getUserId().equals(userId))
                .sorted((a, b) -> Integer.compare(b.getYear(), a.getYear()) != 0
                        ? Integer.compare(b.getYear(), a.getYear())
                        : Integer.compare(b.getMonth(), a.getMonth()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized Optional<Budget> getBudgetByUserAndCategory(String userId, Category category, int month, int year) {
        return budgetCache.values().stream()
                .filter(b -> b.getUserId().equals(userId)
                        && b.getCategory() == category
                        && b.getMonth() == month
                        && b.getYear() == year)
                .findFirst();
    }

    @Override
    public synchronized void updateBudget(Budget budget) {
        if (!budgetCache.containsKey(budget.getId())) {
            throw new StorageException("Cannot update non-existent budget with ID: " + budget.getId());
        }
        budgetCache.put(budget.getId(), budget);
        flushBudgetsToDisk();
    }

    @Override
    public synchronized void deleteBudget(String id) {
        if (budgetCache.remove(id) != null) {
            flushBudgetsToDisk();
        }
    }

    // =========================================================================
    // Goal CRUD
    // =========================================================================

    @Override
    public synchronized void saveGoal(Goal goal) {
        goalCache.put(goal.getId(), goal);
        flushGoalsToDisk();
    }

    @Override
    public synchronized Optional<Goal> getGoalById(String id) {
        return Optional.ofNullable(goalCache.get(id));
    }

    @Override
    public synchronized List<Goal> getGoalsByUserId(String userId) {
        return goalCache.values().stream()
                .filter(g -> g.getUserId().equals(userId))
                .sorted((a, b) -> a.getTargetDate().compareTo(b.getTargetDate()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized void updateGoal(Goal goal) {
        if (!goalCache.containsKey(goal.getId())) {
            throw new StorageException("Cannot update non-existent goal with ID: " + goal.getId());
        }
        goalCache.put(goal.getId(), goal);
        flushGoalsToDisk();
    }

    @Override
    public synchronized void deleteGoal(String id) {
        if (goalCache.remove(id) != null) {
            flushGoalsToDisk();
        }
    }

    // =========================================================================
    // Exchange Rate Cache
    // =========================================================================

    @Override
    public synchronized void saveExchangeRate(ExchangeRate rate) {
        String key = rate.getBaseCurrency().toUpperCase() + "_" + rate.getTargetCurrency().toUpperCase();
        exchangeRateCache.put(key, rate);
        flushExchangeRatesToDisk();
    }

    @Override
    public synchronized Optional<ExchangeRate> getExchangeRate(String baseCurrency, String targetCurrency) {
        if (baseCurrency == null || targetCurrency == null) return Optional.empty();
        String key = baseCurrency.toUpperCase() + "_" + targetCurrency.toUpperCase();
        return Optional.ofNullable(exchangeRateCache.get(key));
    }

    @Override
    public synchronized List<ExchangeRate> getExchangeRatesByBase(String baseCurrency) {
        if (baseCurrency == null) return Collections.emptyList();
        String base = baseCurrency.toUpperCase();
        return exchangeRateCache.values().stream()
                .filter(r -> r.getBaseCurrency().equalsIgnoreCase(base))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Data Interchange (CSV Export & Import)
    // =========================================================================

    @Override
    public synchronized void exportToCsv(String targetDirectoryPath) {
        try {
            Path targetDir = Paths.get(targetDirectoryPath);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Files.copy(usersFile, targetDir.resolve("users.csv"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(transactionsFile, targetDir.resolve("transactions.csv"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(budgetsFile, targetDir.resolve("budgets.csv"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(goalsFile, targetDir.resolve("goals.csv"), StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(exchangeRatesFile)) {
                Files.copy(exchangeRatesFile, targetDir.resolve("exchange_rates.csv"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to export CSV files to target directory: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void importFromCsv(String sourceDirectoryPath) {
        Path sourceDir = Paths.get(sourceDirectoryPath);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new StorageException("Source directory does not exist: " + sourceDirectoryPath);
        }

        try {
            Path sourceUsers = sourceDir.resolve("users.csv");
            if (Files.exists(sourceUsers)) {
                Files.copy(sourceUsers, usersFile, StandardCopyOption.REPLACE_EXISTING);
                loadUsersFromDisk();
            }

            Path sourceTx = sourceDir.resolve("transactions.csv");
            if (Files.exists(sourceTx)) {
                Files.copy(sourceTx, transactionsFile, StandardCopyOption.REPLACE_EXISTING);
                loadTransactionsFromDisk();
            }

            Path sourceBudgets = sourceDir.resolve("budgets.csv");
            if (Files.exists(sourceBudgets)) {
                Files.copy(sourceBudgets, budgetsFile, StandardCopyOption.REPLACE_EXISTING);
                loadBudgetsFromDisk();
            }

            Path sourceGoals = sourceDir.resolve("goals.csv");
            if (Files.exists(sourceGoals)) {
                Files.copy(sourceGoals, goalsFile, StandardCopyOption.REPLACE_EXISTING);
                loadGoalsFromDisk();
            }

            Path sourceExchange = sourceDir.resolve("exchange_rates.csv");
            if (Files.exists(sourceExchange)) {
                Files.copy(sourceExchange, exchangeRatesFile, StandardCopyOption.REPLACE_EXISTING);
                loadExchangeRatesFromDisk();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to import CSV files: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // File Serialization & Deserialization Helpers
    // =========================================================================

    private void loadUsersFromDisk() throws IOException {
        userCache.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile.toFile()))) {
            List<String[]> records = CsvUtil.parseAll(br);
            for (int i = 1; i < records.size(); i++) {
                String[] cols = records.get(i);
                if (cols.length >= 8) {
                    User user = new User(cols[0], cols[1], cols[2], cols[3],
                            new BigDecimal(cols[5]), new BigDecimal(cols[6]), new BigDecimal(cols[7]));
                    user.setReportingCurrency(cols[4]);
                    userCache.put(user.getId(), user);
                } else if (cols.length >= 7) {
                    User user = new User(cols[0], cols[1], cols[2], cols[3],
                            new BigDecimal(cols[4]), new BigDecimal(cols[5]), new BigDecimal(cols[6]));
                    userCache.put(user.getId(), user);
                }
            }
        }
    }

    private void flushUsersToDisk() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(usersFile.toFile()))) {
            bw.write("id,name,email,currency,reporting_currency,monthly_income,global_budget,savings_goal");
            bw.newLine();
            for (User u : userCache.values()) {
                bw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s",
                        escapeCsv(u.getId()), escapeCsv(u.getName()), escapeCsv(u.getEmail()),
                        escapeCsv(u.getCurrency()), escapeCsv(u.getReportingCurrency()),
                        u.getMonthlyIncome(), u.getGlobalBudget(), u.getSavingsGoal()));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to flush users to CSV: " + e.getMessage(), e);
        }
    }

    private void loadTransactionsFromDisk() throws IOException {
        transactionCache.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(transactionsFile.toFile()))) {
            List<String[]> records = CsvUtil.parseAll(br);
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
                    transactionCache.put(t.getId(), t);
                } else if (cols.length >= 8) {
                    String userCurrency = Optional.ofNullable(userCache.get(cols[1]))
                            .map(User::getCurrency).orElse("USD");
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
                    transactionCache.put(t.getId(), t);
                }
            }
        }
    }

    private void flushTransactionsToDisk() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(transactionsFile.toFile()))) {
            bw.write("id,user_id,timestamp,category,amount,currency,payment_mode,type,note");
            bw.newLine();
            for (Transaction t : transactionCache.values()) {
                bw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\",\"%s\",\"%s\"",
                        escapeCsv(t.getId()), escapeCsv(t.getUserId()), t.getTimestamp().format(ISO_FORMATTER),
                        t.getCategory().name(), t.getAmount(), escapeCsv(t.getCurrency()),
                        t.getPaymentMode().name(), t.getType().name(), escapeCsv(t.getNote())));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to flush transactions to CSV: " + e.getMessage(), e);
        }
    }

    private void loadBudgetsFromDisk() throws IOException {
        budgetCache.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(budgetsFile.toFile()))) {
            List<String[]> records = CsvUtil.parseAll(br);
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
                    budgetCache.put(b.getId(), b);
                } else if (cols.length >= 6) {
                    String userCurrency = Optional.ofNullable(userCache.get(cols[1]))
                            .map(User::getCurrency).orElse("USD");
                    Budget b = new Budget(
                            cols[0],
                            cols[1],
                            Category.fromString(cols[2]),
                            new BigDecimal(cols[3]),
                            userCurrency,
                            Integer.parseInt(cols[4]),
                            Integer.parseInt(cols[5])
                    );
                    budgetCache.put(b.getId(), b);
                }
            }
        }
    }

    private void flushBudgetsToDisk() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(budgetsFile.toFile()))) {
            bw.write("id,user_id,category,monthly_limit,currency,month,year");
            bw.newLine();
            for (Budget b : budgetCache.values()) {
                bw.write(String.format("\"%s\",\"%s\",\"%s\",%s,\"%s\",%d,%d",
                        escapeCsv(b.getId()), escapeCsv(b.getUserId()), b.getCategory().name(),
                        b.getMonthlyLimit(), escapeCsv(b.getCurrency()), b.getMonth(), b.getYear()));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to flush budgets to CSV: " + e.getMessage(), e);
        }
    }

    private void loadGoalsFromDisk() throws IOException {
        goalCache.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(goalsFile.toFile()))) {
            List<String[]> records = CsvUtil.parseAll(br);
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
                    goalCache.put(g.getId(), g);
                } else if (cols.length >= 6) {
                    String userCurrency = Optional.ofNullable(userCache.get(cols[1]))
                            .map(User::getCurrency).orElse("USD");
                    Goal g = new Goal(
                            cols[0],
                            cols[1],
                            cols[2],
                            new BigDecimal(cols[3]),
                            new BigDecimal(cols[4]),
                            userCurrency,
                            LocalDate.parse(cols[5])
                    );
                    goalCache.put(g.getId(), g);
                }
            }
        }
    }

    private void flushGoalsToDisk() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(goalsFile.toFile()))) {
            bw.write("id,user_id,name,target_amount,current_amount,currency,target_date");
            bw.newLine();
            for (Goal g : goalCache.values()) {
                bw.write(String.format("\"%s\",\"%s\",\"%s\",%s,%s,\"%s\",\"%s\"",
                        escapeCsv(g.getId()), escapeCsv(g.getUserId()), escapeCsv(g.getName()),
                        g.getTargetAmount(), g.getCurrentAmount(), escapeCsv(g.getCurrency()), g.getTargetDate()));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to flush goals to CSV: " + e.getMessage(), e);
        }
    }

    private void loadExchangeRatesFromDisk() throws IOException {
        exchangeRateCache.clear();
        if (!Files.exists(exchangeRatesFile)) return;
        try (BufferedReader br = new BufferedReader(new FileReader(exchangeRatesFile.toFile()))) {
            List<String[]> records = CsvUtil.parseAll(br);
            for (int i = 1; i < records.size(); i++) {
                String[] cols = records.get(i);
                if (cols.length >= 5) {
                    ExchangeRate rate = new ExchangeRate(
                            cols[0],
                            cols[1],
                            new BigDecimal(cols[2]),
                            LocalDateTime.parse(cols[3], ISO_FORMATTER),
                            cols[4]
                    );
                    String key = rate.getBaseCurrency().toUpperCase() + "_" + rate.getTargetCurrency().toUpperCase();
                    exchangeRateCache.put(key, rate);
                }
            }
        }
    }

    private void flushExchangeRatesToDisk() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(exchangeRatesFile.toFile()))) {
            bw.write("base_currency,target_currency,rate,retrieval_timestamp,provider");
            bw.newLine();
            for (ExchangeRate r : exchangeRateCache.values()) {
                bw.write(String.format("\"%s\",\"%s\",%s,\"%s\",\"%s\"",
                        escapeCsv(r.getBaseCurrency().toUpperCase()),
                        escapeCsv(r.getTargetCurrency().toUpperCase()),
                        r.getRate(),
                        r.getRetrievalTimestamp().format(ISO_FORMATTER),
                        escapeCsv(r.getProvider())));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new StorageException("Failed to flush exchange rates to CSV: " + e.getMessage(), e);
        }
    }

    private String escapeCsv(String input) {
        return CsvUtil.escapeField(input);
    }

    private String[] parseCsvLine(String line) {
        return CsvUtil.parseRecord(line);
    }

    @Override
    public void close() {
        // In-memory write-through ensures data is immediately flushed.
    }
}
