package com.fintrack.controller;

import com.fintrack.model.User;
import com.fintrack.model.ValidationException;
import com.fintrack.storage.StorageEngine;
import com.fintrack.storage.StorageException;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * Controller mediating user profile provisioning, global budget thresholds,
 * profile switching, and database CSV backup/restore operations.
 */
public class UserController implements Initializable {

    @FXML private ComboBox<User> userSelectorComboBox;

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> currencyComboBox;
    @FXML private TextField monthlyIncomeField;
    @FXML private TextField globalBudgetField;
    @FXML private TextField savingsGoalField;
    @FXML private Label userStatusLabel;

    @FXML private Button createUserButton;
    @FXML private Button updateUserButton;
    @FXML private Button deleteUserButton;
    @FXML private Button exportCsvButton;
    @FXML private Button importCsvButton;

    private StorageEngine storageEngine;
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final ObjectProperty<User> activeUserProperty = new SimpleObjectProperty<>();

    public UserController() {}

    public UserController(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
    }

    public void setStorageEngine(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
        loadUsers();
    }

    public ObjectProperty<User> activeUserProperty() {
        return activeUserProperty;
    }

    public User getActiveUser() {
        return activeUserProperty.get();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupCurrencyComboBox();
        setupUserSelector();

        if (userStatusLabel != null) userStatusLabel.setText("");

        if (storageEngine != null) {
            loadUsers();
        }
    }

    private void setupCurrencyComboBox() {
        currencyComboBox.setItems(FXCollections.observableArrayList("USD", "EUR", "GBP", "INR", "CAD", "AUD", "JPY"));
        currencyComboBox.setValue("USD");
    }

    private void setupUserSelector() {
        userSelectorComboBox.setItems(userList);

        // Customize dropdown cell rendering
        userSelectorComboBox.setCellFactory(lv -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.getName() + " (" + user.getEmail() + ")");
            }
        });

        userSelectorComboBox.setButtonCell(new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.getName() + " (" + user.getEmail() + ")");
            }
        });

        userSelectorComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                activeUserProperty.set(newVal);
                populateForm(newVal);
            }
        });
    }

    public void loadUsers() {
        if (storageEngine == null) return;
        try {
            userList.clear();
            userList.addAll(storageEngine.getAllUsers());

            if (!userList.isEmpty()) {
                if (activeUserProperty.get() == null) {
                    userSelectorComboBox.getSelectionModel().select(0);
                } else {
                    userList.stream()
                            .filter(u -> u.getId().equals(activeUserProperty.get().getId()))
                            .findFirst()
                            .ifPresent(u -> userSelectorComboBox.getSelectionModel().select(u));
                }
            } else {
                // Auto-create default initial user if DB is brand new
                User defaultUser = new User("default_user", "Primary User", "user@fintrack.org",
                        "USD", new BigDecimal("5000.00"), new BigDecimal("2500.00"), new BigDecimal("1000.00"));
                storageEngine.saveUser(defaultUser);
                userList.add(defaultUser);
                userSelectorComboBox.getSelectionModel().select(defaultUser);
            }
        } catch (StorageException e) {
            showError("User Loading Error", "Failed to retrieve user profiles", e.getMessage());
        }
    }

    private void populateForm(User user) {
        nameField.setText(user.getName());
        emailField.setText(user.getEmail());
        currencyComboBox.setValue(user.getCurrency());
        monthlyIncomeField.setText(user.getMonthlyIncome().toPlainString());
        globalBudgetField.setText(user.getGlobalBudget().toPlainString());
        savingsGoalField.setText(user.getSavingsGoal().toPlainString());
        if (userStatusLabel != null) userStatusLabel.setText("");
    }

    @FXML
    private void handleCreateUser() {
        try {
            User newUser = parseUserForm(UUID.randomUUID().toString());
            if (storageEngine != null) {
                storageEngine.saveUser(newUser);
                userList.add(newUser);
                userSelectorComboBox.getSelectionModel().select(newUser);
                showSuccess("Profile Created", "New user '" + newUser.getName() + "' created successfully.");
            }
        } catch (ValidationException | StorageException e) {
            displayStatus(e.getMessage(), true);
        }
    }

    @FXML
    private void handleUpdateUser() {
        User active = activeUserProperty.get();
        if (active == null) {
            displayStatus("No active user profile selected to update.", true);
            return;
        }

        try {
            User updated = parseUserForm(active.getId());
            if (storageEngine != null) {
                storageEngine.updateUser(updated);

                int index = userList.indexOf(active);
                if (index >= 0) {
                    userList.set(index, updated);
                }
                activeUserProperty.set(updated);
                displayStatus("Profile updated successfully.", false);
            }
        } catch (ValidationException | StorageException e) {
            displayStatus(e.getMessage(), true);
        }
    }

    @FXML
    private void handleDeleteUser() {
        User active = activeUserProperty.get();
        if (active == null) {
            displayStatus("Select a user to delete.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete User Profile");
        confirm.setContentText("Permanently delete " + active.getName() + "? All associated transactions and budgets will be erased.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                if (storageEngine != null) {
                    storageEngine.deleteUser(active.getId());
                    userList.remove(active);
                    if (!userList.isEmpty()) {
                        userSelectorComboBox.getSelectionModel().select(0);
                    } else {
                        clearForm();
                    }
                    displayStatus("User deleted.", false);
                }
            } catch (StorageException e) {
                showError("Delete Failed", "Could not remove user", e.getMessage());
            }
        }
    }

    @FXML
    private void handleExportCsv() {
        if (storageEngine == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Target Directory for CSV Export");
        Window window = nameField.getScene() != null ? nameField.getScene().getWindow() : null;
        File dir = chooser.showDialog(window);

        if (dir != null) {
            try {
                storageEngine.exportToCsv(dir.getAbsolutePath());
                showSuccess("Export Complete", "Data successfully exported to: " + dir.getAbsolutePath());
            } catch (StorageException e) {
                showError("Export Error", "CSV export failed", e.getMessage());
            }
        }
    }

    @FXML
    private void handleImportCsv() {
        if (storageEngine == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory Containing CSV Files to Import");
        Window window = nameField.getScene() != null ? nameField.getScene().getWindow() : null;
        File dir = chooser.showDialog(window);

        if (dir != null) {
            try {
                storageEngine.importFromCsv(dir.getAbsolutePath());
                loadUsers();
                showSuccess("Import Complete", "CSV data successfully imported into storage engine.");
            } catch (StorageException e) {
                showError("Import Error", "CSV import failed", e.getMessage());
            }
        }
    }

    private User parseUserForm(String id) {
        String name = nameField.getText();
        String email = emailField.getText();
        String currency = currencyComboBox.getValue();

        BigDecimal income = parseMoney(monthlyIncomeField.getText(), "Monthly Income");
        BigDecimal budget = parseMoney(globalBudgetField.getText(), "Global Budget");
        BigDecimal goal = parseMoney(savingsGoalField.getText(), "Savings Goal");

        return new User(id, name, email, currency, income, budget, goal);
    }

    private BigDecimal parseMoney(String text, String fieldName) {
        if (text == null || text.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal val = new BigDecimal(text.trim());
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException(fieldName + " cannot be negative.");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid numeric amount for " + fieldName + ".");
        }
    }

    private void clearForm() {
        nameField.clear();
        emailField.clear();
        currencyComboBox.setValue("USD");
        monthlyIncomeField.clear();
        globalBudgetField.clear();
        savingsGoalField.clear();
    }

    private void displayStatus(String msg, boolean isError) {
        if (userStatusLabel != null) {
            userStatusLabel.setText(msg);
            userStatusLabel.setStyle(isError ? "-fx-text-fill: #d32f2f;" : "-fx-text-fill: #2e7d32;");
        } else if (isError) {
            showError("User Profile Validation", "Validation Failure", msg);
        }
    }

    private void showSuccess(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
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
