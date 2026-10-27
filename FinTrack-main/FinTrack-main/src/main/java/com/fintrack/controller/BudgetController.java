package com.fintrack.controller;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.ValidationException;
import com.fintrack.service.BudgetManager;
import com.fintrack.service.BudgetStatus;
import com.fintrack.service.CategoryBudgetReport;
import com.fintrack.storage.StorageException;
import com.fintrack.util.CurrencyFormatter;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller mediating category budget allocations, visual threshold compliance meters,
 * and financial milestone goals.
 */
public class BudgetController implements Initializable {

    // --- Budget Inputs ---
    @FXML private ComboBox<Category> categoryComboBox;
    @FXML private TextField limitField;
    @FXML private Spinner<Integer> monthSpinner;
    @FXML private Spinner<Integer> yearSpinner;
    @FXML private Label budgetErrorLabel;
    @FXML private Button saveBudgetButton;
    @FXML private Button deleteBudgetButton;

    // --- Budget Compliance Table ---
    @FXML private TableView<CategoryBudgetReport> budgetTable;
    @FXML private TableColumn<CategoryBudgetReport, String> budgetCategoryColumn;
    @FXML private TableColumn<CategoryBudgetReport, String> budgetLimitColumn;
    @FXML private TableColumn<CategoryBudgetReport, String> budgetSpentColumn;
    @FXML private TableColumn<CategoryBudgetReport, String> budgetRemainingColumn;
    @FXML private TableColumn<CategoryBudgetReport, Double> budgetProgressColumn;
    @FXML private TableColumn<CategoryBudgetReport, String> budgetStatusColumn;

    // --- Milestone Goal Inputs ---
    @FXML private TextField goalNameField;
    @FXML private TextField goalTargetField;
    @FXML private DatePicker goalDeadlinePicker;
    @FXML private Label goalErrorLabel;
    @FXML private Button createGoalButton;

    // --- Milestone Goal Contribution ---
    @FXML private TextField contributionField;
    @FXML private Button contributeButton;
    @FXML private Button deleteGoalButton;

    // --- Milestone Goals Table ---
    @FXML private TableView<Goal> goalTable;
    @FXML private TableColumn<Goal, String> goalNameColumn;
    @FXML private TableColumn<Goal, String> goalTargetColumn;
    @FXML private TableColumn<Goal, String> goalCurrentColumn;
    @FXML private TableColumn<Goal, Double> goalProgressColumn;
    @FXML private TableColumn<Goal, String> goalDeadlineColumn;

    private BudgetManager budgetManager;
    private String currentUserId = "default_user";

    private final ObservableList<CategoryBudgetReport> budgetReportList = FXCollections.observableArrayList();
    private final ObservableList<Goal> goalList = FXCollections.observableArrayList();

    public BudgetController() {}

    public BudgetController(BudgetManager budgetManager, String currentUserId) {
        this.budgetManager = budgetManager;
        this.currentUserId = currentUserId;
    }

    public void setBudgetManager(BudgetManager budgetManager) {
        this.budgetManager = budgetManager;
        refreshAll();
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
        refreshAll();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSpinners();
        setupBudgetTable();
        setupGoalTable();

        categoryComboBox.setItems(FXCollections.observableArrayList(Category.values()));
        goalDeadlinePicker.setValue(LocalDate.now().plusMonths(6));

        if (budgetErrorLabel != null) budgetErrorLabel.setText("");
        if (goalErrorLabel != null) goalErrorLabel.setText("");

        if (budgetManager != null) {
            refreshAll();
        }
    }

    private void setupSpinners() {
        YearMonth now = YearMonth.now();
        monthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, now.getMonthValue()));
        yearSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2020, 2100, now.getYear()));

        monthSpinner.valueProperty().addListener((obs, oldVal, newVal) -> refreshBudgets());
        yearSpinner.valueProperty().addListener((obs, oldVal, newVal) -> refreshBudgets());
    }

    private void setupBudgetTable() {
        budgetCategoryColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().category().getDisplayName()));

        budgetLimitColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyFormatter.format(data.getValue().monthlyLimit(), "USD")));
        budgetLimitColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        budgetSpentColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyFormatter.format(data.getValue().spentAmount(), "USD")));
        budgetSpentColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        budgetRemainingColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyFormatter.format(data.getValue().remainingAmount(), "USD")));
        budgetRemainingColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        budgetProgressColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().percentageUsed() / 100.0).asObject());

        // Custom Visual Progress Bar with Dynamic Color Coding
        budgetProgressColumn.setCellFactory(column -> new TableCell<CategoryBudgetReport, Double>() {
            private final ProgressBar progressBar = new ProgressBar();

            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    progressBar.setProgress(Math.min(1.0, progress));
                    progressBar.setMaxWidth(Double.MAX_VALUE);

                    // Dynamic CSS bar color based on percentage
                    if (progress < 0.75) {
                        progressBar.setStyle("-fx-accent: #2e7d32;"); // Green
                    } else if (progress <= 1.0) {
                        progressBar.setStyle("-fx-accent: #f57f17;"); // Amber / Orange
                    } else {
                        progressBar.setStyle("-fx-accent: #c62828;"); // Red (Exceeded)
                    }
                    setGraphic(progressBar);
                }
            }
        });

        // Compliance Status Cell with Color Highlights
        budgetStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().status().name()));

        budgetStatusColumn.setCellFactory(col -> new TableCell<CategoryBudgetReport, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    if (BudgetStatus.OK.name().equals(status)) {
                        setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    } else if (BudgetStatus.WARNING.name().equals(status)) {
                        setStyle("-fx-text-fill: #ef6c00; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    }
                }
            }
        });

        budgetTable.setItems(budgetReportList);
    }

    private void setupGoalTable() {
        goalNameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName()));

        goalTargetColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyFormatter.format(data.getValue().getTargetAmount(), "USD")));
        goalTargetColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        goalCurrentColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyFormatter.format(data.getValue().getCurrentAmount(), "USD")));
        goalCurrentColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        goalProgressColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getProgressPercentage() / 100.0).asObject());

        goalProgressColumn.setCellFactory(column -> new TableCell<Goal, Double>() {
            private final ProgressBar bar = new ProgressBar();
            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    bar.setProgress(Math.min(1.0, progress));
                    bar.setMaxWidth(Double.MAX_VALUE);
                    bar.setStyle("-fx-accent: #1976d2;");
                    setGraphic(bar);
                }
            }
        });

        goalDeadlineColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getTargetDate().toString()));

        goalTable.setItems(goalList);
    }

    public void refreshAll() {
        refreshBudgets();
        refreshGoals();
    }

    public void refreshBudgets() {
        if (budgetManager == null) return;
        try {
            int month = monthSpinner.getValue() != null ? monthSpinner.getValue() : YearMonth.now().getMonthValue();
            int year = yearSpinner.getValue() != null ? yearSpinner.getValue() : YearMonth.now().getYear();

            budgetReportList.clear();
            budgetReportList.addAll(budgetManager.getAllCategoryBudgetReports(currentUserId, month, year));
        } catch (StorageException e) {
            showError("Storage Failure", "Unable to refresh category budgets", e.getMessage());
        }
    }

    public void refreshGoals() {
        if (budgetManager == null) return;
        try {
            goalList.clear();
            goalList.addAll(budgetManager.getUserGoals(currentUserId));
        } catch (StorageException e) {
            showError("Storage Failure", "Unable to refresh milestone goals", e.getMessage());
        }
    }

    @FXML
    private void handleSaveBudget() {
        if (budgetErrorLabel != null) budgetErrorLabel.setText("");

        Category category = categoryComboBox.getValue();
        if (category == null) {
            showBudgetError("Select an expense category.");
            return;
        }

        String limitText = limitField.getText();
        if (limitText == null || limitText.isBlank()) {
            showBudgetError("Monthly spending limit cannot be blank.");
            return;
        }

        BigDecimal limit;
        try {
            limit = new BigDecimal(limitText.trim());
            if (limit.compareTo(BigDecimal.ZERO) <= 0) {
                showBudgetError("Limit must be strictly positive (> 0.00).");
                return;
            }
        } catch (NumberFormatException e) {
            showBudgetError("Enter a valid numeric amount for the budget limit.");
            return;
        }

        int month = monthSpinner.getValue();
        int year = yearSpinner.getValue();

        try {
            if (budgetManager != null) {
                budgetManager.setCategoryBudget(currentUserId, category, limit, month, year);
                refreshBudgets();
            }
            limitField.clear();
            categoryComboBox.getSelectionModel().clearSelection();
        } catch (ValidationException | StorageException e) {
            showBudgetError(e.getMessage());
        }
    }

    @FXML
    private void handleDeleteBudget() {
        CategoryBudgetReport selected = budgetTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Budget Selected", "Select a budget entry from the table to delete.");
            return;
        }

        int month = monthSpinner.getValue();
        int year = yearSpinner.getValue();

        Optional<Budget> budgetOpt = budgetManager.getCategoryBudget(currentUserId, selected.category(), month, year);
        if (budgetOpt.isPresent()) {
            budgetManager.deleteBudget(budgetOpt.get().getId());
            refreshBudgets();
        }
    }

    @FXML
    private void handleCreateGoal() {
        if (goalErrorLabel != null) goalErrorLabel.setText("");

        String name = goalNameField.getText();
        if (name == null || name.isBlank()) {
            showGoalError("Goal title cannot be empty.");
            return;
        }

        String targetText = goalTargetField.getText();
        if (targetText == null || targetText.isBlank()) {
            showGoalError("Target amount cannot be empty.");
            return;
        }

        BigDecimal targetAmount;
        try {
            targetAmount = new BigDecimal(targetText.trim());
            if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
                showGoalError("Target amount must be strictly greater than 0.00.");
                return;
            }
        } catch (NumberFormatException e) {
            showGoalError("Enter a valid numeric monetary amount for the target.");
            return;
        }

        LocalDate deadline = goalDeadlinePicker.getValue();
        if (deadline == null) {
            showGoalError("Select a target deadline date.");
            return;
        }

        try {
            if (budgetManager != null) {
                budgetManager.createGoal(currentUserId, name.trim(), targetAmount, deadline);
                refreshGoals();
            }
            goalNameField.clear();
            goalTargetField.clear();
        } catch (ValidationException | StorageException e) {
            showGoalError(e.getMessage());
        }
    }

    @FXML
    private void handleContributeToGoal() {
        Goal selected = goalTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Goal Selected", "Select a milestone goal to contribute to.");
            return;
        }

        String text = contributionField.getText();
        if (text == null || text.isBlank()) {
            showGoalError("Enter contribution amount.");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(text.trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showGoalError("Contribution must be greater than 0.00.");
                return;
            }
            if (budgetManager != null) {
                budgetManager.contributeToGoal(selected.getId(), amount);
                refreshGoals();
            }
            contributionField.clear();
        } catch (NumberFormatException e) {
            showGoalError("Enter a valid numeric contribution amount.");
        } catch (ValidationException | StorageException e) {
            showGoalError(e.getMessage());
        }
    }

    @FXML
    private void handleDeleteGoal() {
        Goal selected = goalTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Goal Selected", "Select a milestone goal to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Goal");
        confirm.setHeaderText("Delete Milestone Goal");
        confirm.setContentText("Are you sure you want to remove the goal: " + selected.getName() + "?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (budgetManager != null) {
                budgetManager.deleteGoal(selected.getId());
                refreshGoals();
            }
        }
    }

    private void showBudgetError(String msg) {
        if (budgetErrorLabel != null) budgetErrorLabel.setText(msg);
        else showWarning("Budget Error", msg);
    }

    private void showGoalError(String msg) {
        if (goalErrorLabel != null) goalErrorLabel.setText(msg);
        else showWarning("Goal Error", msg);
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
