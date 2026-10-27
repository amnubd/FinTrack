package com.fintrack.controller;

import com.fintrack.model.Category;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.model.ValidationException;
import com.fintrack.storage.StorageEngine;
import com.fintrack.storage.StorageException;
import com.fintrack.util.CurrencyFormatter;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller mediating transaction data ingestion forms, real-time filtering,
 * and tabular history presentation.
 */
public class TransactionController implements Initializable {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private ComboBox<Category> categoryComboBox;
    @FXML private TextField amountField;
    @FXML private ComboBox<PaymentMode> paymentModeComboBox;
    @FXML private ComboBox<TransactionType> typeComboBox;
    @FXML private DatePicker datePicker;
    @FXML private TextField noteField;
    @FXML private Label formErrorLabel;

    @FXML private Button addButton;
    @FXML private Button clearButton;
    @FXML private Button deleteButton;

    // Filter controls
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterComboBox;
    @FXML private ComboBox<Category> categoryFilterComboBox;

    // Table and Columns
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> dateColumn;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, String> categoryColumn;
    @FXML private TableColumn<Transaction, String> amountColumn;
    @FXML private TableColumn<Transaction, String> paymentModeColumn;
    @FXML private TableColumn<Transaction, String> noteColumn;

    private StorageEngine storageEngine;
    private String currentUserId = "default_user";

    private final ObservableList<Transaction> transactionMasterList = FXCollections.observableArrayList();
    private FilteredList<Transaction> filteredTransactions;

    public TransactionController() {
        // Default constructor required by JavaFX FXMLLoader
    }

    public TransactionController(StorageEngine storageEngine, String currentUserId) {
        this.storageEngine = storageEngine;
        this.currentUserId = currentUserId;
    }

    public void setStorageEngine(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
        loadTransactions();
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
        loadTransactions();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupFormInputs();
        setupTableColumns();
        setupFiltering();

        if (storageEngine != null) {
            loadTransactions();
        }
    }

    private void setupFormInputs() {
        categoryComboBox.setItems(FXCollections.observableArrayList(Category.values()));
        paymentModeComboBox.setItems(FXCollections.observableArrayList(PaymentMode.values()));
        typeComboBox.setItems(FXCollections.observableArrayList(TransactionType.values()));
        typeComboBox.setValue(TransactionType.EXPENSE);
        datePicker.setValue(LocalDate.now());

        if (formErrorLabel != null) {
            formErrorLabel.setText("");
        }

        // Auto-select typical type when category changes
        categoryComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                typeComboBox.setValue(newVal.getDefaultType());
            }
        });
    }

    private void setupTableColumns() {
        dateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getTimestamp().format(DATE_FORMATTER)));

        typeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getType().getDisplayName()));

        categoryColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCategory().getDisplayName()));

        amountColumn.setCellValueFactory(cellData -> {
            Transaction t = cellData.getValue();
            String curr = (storageEngine != null && currentUserId != null)
                    ? storageEngine.getUserById(currentUserId).map(User::getCurrency).orElse("USD")
                    : "USD";
            return new SimpleStringProperty(CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), curr));
        });
        amountColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        paymentModeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getPaymentMode().getDisplayName()));

        noteColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getNote()));
    }

    private void setupFiltering() {
        filteredTransactions = new FilteredList<>(transactionMasterList, p -> true);

        ObservableList<String> typeFilterOptions = FXCollections.observableArrayList("ALL", "EXPENSE", "INCOME");
        typeFilterComboBox.setItems(typeFilterOptions);
        typeFilterComboBox.setValue("ALL");

        ObservableList<Category> categoryFilterOptions = FXCollections.observableArrayList();
        categoryFilterOptions.add(null); // represents "All Categories"
        categoryFilterOptions.addAll(Category.values());
        categoryFilterComboBox.setItems(categoryFilterOptions);

        // Re-filter when any search/filter control changes
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        typeFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        categoryFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        SortedList<Transaction> sortedData = new SortedList<>(filteredTransactions);
        sortedData.comparatorProperty().bind(transactionTable.comparatorProperty());
        transactionTable.setItems(sortedData);
    }

    private void applyFilter() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String typeFilter = typeFilterComboBox.getValue();
        Category catFilter = categoryFilterComboBox.getValue();

        filteredTransactions.setPredicate(tx -> {
            // Type match
            if (typeFilter != null && !typeFilter.equals("ALL")) {
                if (!tx.getType().name().equalsIgnoreCase(typeFilter)) {
                    return false;
                }
            }
            // Category match
            if (catFilter != null) {
                if (tx.getCategory() != catFilter) {
                    return false;
                }
            }
            // Keyword match (note, category name, amount)
            if (!keyword.isEmpty()) {
                boolean matchesNote = tx.getNote() != null && tx.getNote().toLowerCase().contains(keyword);
                boolean matchesCategory = tx.getCategory().getDisplayName().toLowerCase().contains(keyword);
                boolean matchesAmount = tx.getAmount().toPlainString().contains(keyword);
                return matchesNote || matchesCategory || matchesAmount;
            }
            return true;
        });
    }

    /**
     * Loads user transactions from storage and populates master observable list.
     */
    public void loadTransactions() {
        if (storageEngine == null) return;
        try {
            transactionMasterList.clear();
            transactionMasterList.addAll(storageEngine.getTransactionsByUserId(currentUserId));
        } catch (StorageException e) {
            showError("Storage Failure", "Unable to load transaction records", e.getMessage());
        }
    }

    @FXML
    private void handleAddTransaction() {
        if (formErrorLabel != null) formErrorLabel.setText("");

        // 1. Validate Category
        Category category = categoryComboBox.getValue();
        if (category == null) {
            displayFormError("Please select a valid transaction category.");
            return;
        }

        // 2. Validate Amount
        String amountText = amountField.getText();
        if (amountText == null || amountText.isBlank()) {
            displayFormError("Transaction amount cannot be empty.");
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                displayFormError("Amount must be strictly greater than 0.00.");
                return;
            }
        } catch (NumberFormatException e) {
            displayFormError("Please enter a valid numeric monetary amount (e.g., 45.50).");
            return;
        }

        // 3. Validate Payment Mode & Type
        PaymentMode paymentMode = paymentModeComboBox.getValue();
        if (paymentMode == null) {
            displayFormError("Please select a settlement payment mode.");
            return;
        }

        TransactionType type = typeComboBox.getValue();
        if (type == null) {
            displayFormError("Please specify the transaction type (INCOME or EXPENSE).");
            return;
        }

        // 4. Validate Timestamp
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            displayFormError("Please select a valid transaction date.");
            return;
        }
        LocalDateTime timestamp = selectedDate.atTime(LocalTime.now());

        String note = (noteField.getText() == null) ? "" : noteField.getText().trim();

        try {
            Transaction transaction = new Transaction(
                    java.util.UUID.randomUUID().toString(),
                    currentUserId,
                    timestamp,
                    category,
                    amount,
                    paymentMode,
                    type,
                    note
            );

            if (storageEngine != null) {
                storageEngine.saveTransaction(transaction);
            }

            transactionMasterList.add(0, transaction);
            clearForm();
        } catch (ValidationException | StorageException e) {
            displayFormError(e.getMessage());
        }
    }

    @FXML
    private void handleDeleteTransaction() {
        Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Selection", "Select a transaction to delete from the table.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Transaction Record");
        confirm.setContentText(String.format("Are you sure you want to delete the transaction of %s for '%s'?",
                selected.getAmount().toPlainString(), selected.getCategory().getDisplayName()));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (storageEngine != null) {
                    storageEngine.deleteTransaction(selected.getId());
                }
                transactionMasterList.remove(selected);
            } catch (StorageException e) {
                showError("Delete Failed", "Could not remove transaction", e.getMessage());
            }
        }
    }

    @FXML
    private void handleClearForm() {
        clearForm();
    }

    private void clearForm() {
        amountField.clear();
        noteField.clear();
        categoryComboBox.getSelectionModel().clearSelection();
        paymentModeComboBox.getSelectionModel().clearSelection();
        typeComboBox.setValue(TransactionType.EXPENSE);
        datePicker.setValue(LocalDate.now());
        if (formErrorLabel != null) {
            formErrorLabel.setText("");
        }
    }

    private void displayFormError(String message) {
        if (formErrorLabel != null) {
            formErrorLabel.setText(message);
        } else {
            showWarning("Form Validation Error", message);
        }
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
