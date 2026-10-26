package com.fintrack.storage;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.Transaction;
import com.fintrack.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Common data persistence abstraction defining CRUD operations and batch interchange
 * for users, transactions, category budgets, and financial milestone goals.
 */
public interface StorageEngine extends AutoCloseable {

    /**
     * Initializes the underlying storage infrastructure (e.g., executing DDL migrations,
     * ensuring directory trees and initial flat files exist).
     *
     * @throws StorageException if initialization fails
     */
    void initialize();

    // =========================================================================
    // User CRUD
    // =========================================================================

    /**
     * Persists a new user record.
     *
     * @param user user entity to store
     * @throws StorageException if persistence fails or primary key conflicts
     */
    void saveUser(User user);

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id user ID
     * @return an {@link Optional} containing the user if found, or empty
     * @throws StorageException on storage access failure
     */
    Optional<User> getUserById(String id);

    /**
     * Retrieves a user by their registered email address.
     *
     * @param email unique email address
     * @return an {@link Optional} containing the user if found, or empty
     * @throws StorageException on storage access failure
     */
    Optional<User> getUserByEmail(String email);

    /**
     * Retrieves all registered users.
     *
     * @return list of all users
     * @throws StorageException on storage access failure
     */
    List<User> getAllUsers();

    /**
     * Updates an existing user's attributes.
     *
     * @param user user entity with updated fields
     * @throws StorageException if update fails or record does not exist
     */
    void updateUser(User user);

    /**
     * Deletes a user by their identifier. Cascades to associated transactions, budgets, and goals.
     *
     * @param id user ID to delete
     * @throws StorageException on storage access failure
     */
    void deleteUser(String id);

    // =========================================================================
    // Transaction CRUD
    // =========================================================================

    /**
     * Persists a new financial transaction.
     *
     * @param transaction transaction entity to store
     * @throws StorageException on storage access failure
     */
    void saveTransaction(Transaction transaction);

    /**
     * Retrieves a transaction by its unique identifier.
     *
     * @param id transaction ID
     * @return an {@link Optional} containing the transaction if found, or empty
     * @throws StorageException on storage access failure
     */
    Optional<Transaction> getTransactionById(String id);

    /**
     * Retrieves all recorded transactions across the system.
     *
     * @return list of all transactions
     * @throws StorageException on storage access failure
     */
    List<Transaction> getAllTransactions();

    /**
     * Retrieves all transactions associated with a specific user.
     *
     * @param userId user identifier
     * @return list of matching transactions
     * @throws StorageException on storage access failure
     */
    List<Transaction> getTransactionsByUserId(String userId);

    /**
     * Retrieves transactions for a user within an inclusive timestamp range.
     *
     * @param userId user identifier
     * @param start  start of timestamp window
     * @param end    end of timestamp window
     * @return list of matching transactions
     * @throws StorageException on storage access failure
     */
    List<Transaction> getTransactionsByDateRange(String userId, LocalDateTime start, LocalDateTime end);

    /**
     * Updates an existing transaction record.
     *
     * @param transaction transaction entity containing updated attributes
     * @throws StorageException if update fails or record does not exist
     */
    void updateTransaction(Transaction transaction);

    /**
     * Deletes a transaction by its unique identifier.
     *
     * @param id transaction ID to delete
     * @throws StorageException on storage access failure
     */
    void deleteTransaction(String id);

    // =========================================================================
    // Budget CRUD
    // =========================================================================

    /**
     * Persists a new monthly category budget limit.
     *
     * @param budget budget entity to store
     * @throws StorageException on storage access failure
     */
    void saveBudget(Budget budget);

    /**
     * Retrieves a budget limit by its unique identifier.
     *
     * @param id budget ID
     * @return an {@link Optional} containing the budget if found, or empty
     * @throws StorageException on storage access failure
     */
    Optional<Budget> getBudgetById(String id);

    /**
     * Retrieves all category budgets configured for a user.
     *
     * @param userId user identifier
     * @return list of user's budgets
     * @throws StorageException on storage access failure
     */
    List<Budget> getBudgetsByUserId(String userId);

    /**
     * Retrieves the budget limit for a specific category, month, and year.
     *
     * @param userId   user identifier
     * @param category expense category
     * @param month    calendar month (1 - 12)
     * @param year     calendar year
     * @return an {@link Optional} with the matching budget if configured, or empty
     * @throws StorageException on storage access failure
     */
    Optional<Budget> getBudgetByUserAndCategory(String userId, Category category, int month, int year);

    /**
     * Updates an existing category budget limit.
     *
     * @param budget budget entity containing updated values
     * @throws StorageException on storage access failure
     */
    void updateBudget(Budget budget);

    /**
     * Deletes a budget configuration by its unique identifier.
     *
     * @param id budget ID to delete
     * @throws StorageException on storage access failure
     */
    void deleteBudget(String id);

    // =========================================================================
    // Goal CRUD
    // =========================================================================

    /**
     * Persists a new financial milestone savings goal.
     *
     * @param goal goal entity to store
     * @throws StorageException on storage access failure
     */
    void saveGoal(Goal goal);

    /**
     * Retrieves a goal by its unique identifier.
     *
     * @param id goal ID
     * @return an {@link Optional} containing the goal if found, or empty
     * @throws StorageException on storage access failure
     */
    Optional<Goal> getGoalById(String id);

    /**
     * Retrieves all milestone goals configured for a user.
     *
     * @param userId user identifier
     * @return list of goals
     * @throws StorageException on storage access failure
     */
    List<Goal> getGoalsByUserId(String userId);

    /**
     * Updates an existing financial milestone savings goal.
     *
     * @param goal goal entity containing updated values
     * @throws StorageException on storage access failure
     */
    void updateGoal(Goal goal);

    /**
     * Deletes a milestone goal by its unique identifier.
     *
     * @param id goal ID to delete
     * @throws StorageException on storage access failure
     */
    void deleteGoal(String id);

    // =========================================================================
    // Data Interchange (CSV Export & Import)
    // =========================================================================

    /**
     * Exports all user, transaction, budget, and goal data into CSV files inside the target directory.
     *
     * @param targetDirectoryPath directory path where CSV files will be generated
     * @throws StorageException if export fails
     */
    void exportToCsv(String targetDirectoryPath);

    /**
     * Imports records from CSV files in the source directory into the active storage engine.
     *
     * @param sourceDirectoryPath directory containing CSV files to import
     * @throws StorageException if parsing or persistence fails
     */
    void importFromCsv(String sourceDirectoryPath);

    /**
     * Closes open storage resources, file channels, or database connections.
     *
     * @throws StorageException on resource closing failure
     */
    @Override
    void close();
}
