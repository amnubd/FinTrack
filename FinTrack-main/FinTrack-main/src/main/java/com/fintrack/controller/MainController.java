package com.fintrack.controller;

import com.fintrack.model.Budget;
import com.fintrack.model.Category;
import com.fintrack.model.Goal;
import com.fintrack.model.PaymentMode;
import com.fintrack.model.Transaction;
import com.fintrack.model.TransactionType;
import com.fintrack.model.User;
import com.fintrack.model.ValidationException;
import com.fintrack.service.AnalyticsProcessor;
import com.fintrack.service.BudgetManager;
import com.fintrack.service.BudgetStatus;
import com.fintrack.service.CategoryBudgetReport;
import com.fintrack.service.CurrencyConversionException;
import com.fintrack.service.CurrencyConversionService;
import com.fintrack.service.ExchangeRateProvider;
import com.fintrack.service.HttpExchangeRateProvider;
import com.fintrack.service.dto.CategoryRunRate;
import com.fintrack.service.dto.CategorySpendingShare;
import com.fintrack.service.dto.ForecastResult;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.service.dto.MonthlyForecast;
import com.fintrack.service.dto.VelocityMetrics;
import com.fintrack.storage.SQLiteStorageEngine;
import com.fintrack.storage.StorageEngine;
import com.fintrack.storage.StorageException;
import com.fintrack.util.CurrencyFormatter;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Primary coordinator and controller orchestrating the main application view,
 * sidebar navigation, real-time data synchronization across all 7 functional tabs,
 * and presentation bindings.
 */
public class MainController implements Initializable {

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // --- Sidebar & Navigation ---
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavAddTx;
    @FXML private Button btnNavTransactions;
    @FXML private Button btnNavBudgets;
    @FXML private Button btnNavGoals;
    @FXML private Button btnNavReports;
    @FXML private Button btnNavProfile;
    @FXML private Label lblSidebarUserName;
    @FXML private Label lblSidebarUserEmail;
    @FXML private TabPane mainTabPane;

    // --- Tab 1: Dashboard Widgets ---
    @FXML private Label lblKpiIncome;
    @FXML private Label lblKpiExpenses;
    @FXML private Label lblKpiBalance;
    @FXML private Label lblKpiSavingsRate;
    @FXML private PieChart chartCategoryPie;
    @FXML private BarChart<String, Number> chartMonthlyTrends;
    @FXML private TableView<Transaction> tableDashboardRecent;
    @FXML private TableColumn<Transaction, String> colDashDate;
    @FXML private TableColumn<Transaction, String> colDashType;
    @FXML private TableColumn<Transaction, String> colDashCategory;
    @FXML private TableColumn<Transaction, String> colDashAmount;
    @FXML private TableColumn<Transaction, String> colDashNote;

    // --- Tab 2: Add Transaction Widgets ---
    @FXML private ComboBox<TransactionType> comboAddTxType;
    @FXML private ComboBox<Category> comboAddTxCategory;
    @FXML private TextField txtAddTxAmount;
    @FXML private ComboBox<String> comboAddTxCurrency;
    @FXML private ComboBox<PaymentMode> comboAddTxPaymentMode;
    @FXML private DatePicker pickerAddTxDate;
    @FXML private TextArea txtAddTxNote;
    @FXML private Label lblAddTxError;

    // --- Tab 3: Transactions Ledger Widgets ---
    @FXML private TextField txtTxSearch;
    @FXML private ComboBox<String> comboTxTypeFilter;
    @FXML private ComboBox<Category> comboTxCategoryFilter;
    @FXML private TableView<Transaction> tableTransactions;
    @FXML private TableColumn<Transaction, String> colTxDate;
    @FXML private TableColumn<Transaction, String> colTxType;
    @FXML private TableColumn<Transaction, String> colTxCategory;
    @FXML private TableColumn<Transaction, String> colTxAmount;
    @FXML private TableColumn<Transaction, String> colTxPaymentMode;
    @FXML private TableColumn<Transaction, String> colTxNote;

    // --- Tab 4: Budgets Widgets ---
    @FXML private ComboBox<Category> comboBudgetCategory;
    @FXML private TextField txtBudgetLimit;
    @FXML private Spinner<Integer> spinnerBudgetMonth;
    @FXML private Spinner<Integer> spinnerBudgetYear;
    @FXML private Label lblBudgetError;
    @FXML private TableView<CategoryBudgetReport> tableBudgets;
    @FXML private TableColumn<CategoryBudgetReport, String> colBudgetCategory;
    @FXML private TableColumn<CategoryBudgetReport, String> colBudgetLimit;
    @FXML private TableColumn<CategoryBudgetReport, String> colBudgetSpent;
    @FXML private TableColumn<CategoryBudgetReport, String> colBudgetRemaining;
    @FXML private TableColumn<CategoryBudgetReport, Double> colBudgetProgress;
    @FXML private TableColumn<CategoryBudgetReport, String> colBudgetStatus;

    // --- Tab 5: Goals Widgets ---
    @FXML private TextField txtGoalName;
    @FXML private TextField txtGoalTarget;
    @FXML private DatePicker pickerGoalDate;
    @FXML private Label lblGoalError;
    @FXML private TextField txtGoalContribution;
    @FXML private TableView<Goal> tableGoals;
    @FXML private TableColumn<Goal, String> colGoalName;
    @FXML private TableColumn<Goal, String> colGoalTarget;
    @FXML private TableColumn<Goal, String> colGoalCurrent;
    @FXML private TableColumn<Goal, Double> colGoalProgress;
    @FXML private TableColumn<Goal, String> colGoalDeadline;

    // --- Tab 6: Reports & Forecast Widgets ---
    @FXML private ComboBox<Integer> comboVelocityDays;
    @FXML private Label lblReportDailySpend;
    @FXML private Label lblReportDailyTx;
    @FXML private Label lblReportWeeklySpend;
    @FXML private Label lblReportWeeklyTx;
    @FXML private Label lblReportHealthScore;
    @FXML private Label lblReportHealthTier;
    @FXML private ProgressBar progReportHealth;
    @FXML private Label lblReportHealthInsight;
    @FXML private Label lblReportMonthEndForecast;
    @FXML private TableView<MonthlyForecast> tableForecast;
    @FXML private TableColumn<MonthlyForecast, String> colForecastPeriod;
    @FXML private TableColumn<MonthlyForecast, String> colForecastIncome;
    @FXML private TableColumn<MonthlyForecast, String> colForecastExpense;
    @FXML private TableColumn<MonthlyForecast, String> colForecastNet;
    @FXML private TableColumn<MonthlyForecast, String> colForecastBalance;

    // --- Tab 7: Profile Widgets ---
    @FXML private TextField txtProfileName;
    @FXML private TextField txtProfileEmail;
    @FXML private ComboBox<String> comboProfileCurrency;
    @FXML private ComboBox<String> comboProfileReportingCurrency;
    @FXML private TextField txtProfileIncome;
    @FXML private TextField txtProfileGlobalBudget;
    @FXML private TextField txtProfileSavingsGoal;
    @FXML private Label lblProfileStatus;
    @FXML private Label lblExchangeRateStatus;

    // --- Infrastructure & Services ---
    private StorageEngine storageEngine;
    private CurrencyConversionService conversionService;
    private BudgetManager budgetManager;
    private AnalyticsProcessor analyticsProcessor;
    private User activeUser;

    // Master Observable Collections
    private final ObservableList<Transaction> masterTransactions = FXCollections.observableArrayList();
    private FilteredList<Transaction> filteredTransactions;
    private final ObservableList<CategoryBudgetReport> masterBudgets = FXCollections.observableArrayList();
    private final ObservableList<Goal> masterGoals = FXCollections.observableArrayList();
    private final ObservableList<MonthlyForecast> masterForecasts = FXCollections.observableArrayList();

    public MainController() {}

    public MainController(StorageEngine storageEngine) {
        this(storageEngine, null);
    }

    public MainController(StorageEngine storageEngine, CurrencyConversionService conversionService) {
        this.storageEngine = storageEngine;
        this.conversionService = conversionService;
    }

    public void setConversionService(CurrencyConversionService conversionService) {
        this.conversionService = conversionService;
        if (storageEngine != null) {
            this.budgetManager = new BudgetManager(storageEngine, conversionService);
            this.analyticsProcessor = new AnalyticsProcessor(storageEngine, budgetManager, conversionService);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initInfrastructure();
        setupAddTransactionForm();
        setupTransactionsLedger();
        setupBudgetsView();
        setupGoalsView();
        setupReportsView();
        setupProfileView();
        setupDashboardView();

        // Initial full data synchronization
        refreshAllData();
    }

    private void initInfrastructure() {
        if (storageEngine == null) {
            storageEngine = new SQLiteStorageEngine("fintrack.db");
        }
        if (conversionService == null) {
            conversionService = new CurrencyConversionService(new HttpExchangeRateProvider(), storageEngine);
        }
        budgetManager = new BudgetManager(storageEngine, conversionService);
        analyticsProcessor = new AnalyticsProcessor(storageEngine, budgetManager, conversionService);

        // Fetch or initialize default user
        List<User> users = storageEngine.getAllUsers();
        if (users.isEmpty()) {
            activeUser = new User("user_main", "Primary Account Holder", "finance@fintrack.org",
                    "USD", new BigDecimal("7500.00"), new BigDecimal("3200.00"), new BigDecimal("1500.00"));
            activeUser.setReportingCurrency("USD");
            storageEngine.saveUser(activeUser);
        } else {
            activeUser = users.get(0);
        }

        updateSidebarUserInfo();
    }

    private String getBaseCurrency() {
        return (activeUser != null && activeUser.getCurrency() != null && !activeUser.getCurrency().isBlank())
                ? activeUser.getCurrency()
                : "USD";
    }

    private String getReportingCurrency() {
        if (activeUser != null && activeUser.getReportingCurrency() != null && !activeUser.getReportingCurrency().isBlank()) {
            return activeUser.getReportingCurrency();
        }
        return getBaseCurrency();
    }

    private String getActiveCurrency() {
        return getReportingCurrency();
    }

    private String getActiveCurrencySymbol() {
        return CurrencyFormatter.getCurrencySymbol(getActiveCurrency());
    }

    private void updateSidebarUserInfo() {
        if (activeUser != null) {
            if (lblSidebarUserName != null) lblSidebarUserName.setText(activeUser.getName());
            if (lblSidebarUserEmail != null) lblSidebarUserEmail.setText(activeUser.getEmail());
        }
    }

    // =========================================================================
    // SIDEBAR NAVIGATION HANDLERS
    // =========================================================================

    @FXML public void showDashboardTab() { selectTab(0, btnNavDashboard); }
    @FXML public void showAddTransactionTab() { selectTab(1, btnNavAddTx); }
    @FXML public void showTransactionsTab() { selectTab(2, btnNavTransactions); }
    @FXML public void showBudgetsTab() { selectTab(3, btnNavBudgets); }
    @FXML public void showGoalsTab() { selectTab(4, btnNavGoals); }
    @FXML public void showReportsTab() { selectTab(5, btnNavReports); }
    @FXML public void showProfileTab() { selectTab(6, btnNavProfile); }

    private void selectTab(int index, Button activeNavBtn) {
        mainTabPane.getSelectionModel().select(index);

        List<Button> navButtons = List.of(
                btnNavDashboard, btnNavAddTx, btnNavTransactions,
                btnNavBudgets, btnNavGoals, btnNavReports, btnNavProfile
        );

        for (Button btn : navButtons) {
            if (btn != null) {
                btn.getStyleClass().remove("active");
            }
        }
        if (activeNavBtn != null && !activeNavBtn.getStyleClass().contains("active")) {
            activeNavBtn.getStyleClass().add("active");
        }

        // On tab switch, refresh contextual tab contents
        refreshContextualTab(index);
    }

    private void refreshContextualTab(int index) {
        switch (index) {
            case 0 -> refreshDashboard();
            case 2 -> refreshTransactionsList();
            case 3 -> refreshBudgets();
            case 4 -> refreshGoals();
            case 5 -> refreshReports();
            case 6 -> populateProfileForm();
        }
        updateDynamicCurrencyLabels();
    }

    // =========================================================================
    // TAB 1: DASHBOARD
    // =========================================================================

    private void setupDashboardView() {
        tableDashboardRecent.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colDashDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTimestamp().format(DT_FORMATTER)));
        colDashType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType().getDisplayName()));
        colDashCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory().getDisplayName()));
        colDashAmount.setCellValueFactory(c -> {
            Transaction t = c.getValue();
            String repCurr = getReportingCurrency();
            if (t.getCurrency() != null && !t.getCurrency().equalsIgnoreCase(repCurr) && conversionService != null) {
                try {
                    BigDecimal converted = conversionService.convert(t.getAmount(), t.getCurrency(), repCurr);
                    String orig = CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), t.getCurrency());
                    String conv = CurrencyFormatter.format(converted, repCurr);
                    return new SimpleStringProperty(orig + " (~" + conv + ")");
                } catch (Exception e) {
                    return new SimpleStringProperty(CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), t.getCurrency()));
                }
            }
            return new SimpleStringProperty(CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), repCurr));
        });
        colDashAmount.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colDashNote.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNote()));
    }

    private void refreshDashboard() {
        if (analyticsProcessor == null || activeUser == null) return;

        HealthSummary health = analyticsProcessor.computeHealthSummary(activeUser.getId());
        BigDecimal currentBalance = analyticsProcessor.computeCurrentBalance(activeUser.getId());

        lblKpiIncome.setText(CurrencyFormatter.format(health.totalIncome(), getActiveCurrency()));
        lblKpiExpenses.setText(CurrencyFormatter.format(health.totalExpenses(), getActiveCurrency()));
        lblKpiBalance.setText(CurrencyFormatter.formatSignedBalance(currentBalance, getActiveCurrency()));
        lblKpiSavingsRate.setText(String.format("%.1f%%", health.savingsRate()));

        // Update Y-Axis label with active currency
        if (chartMonthlyTrends != null && chartMonthlyTrends.getYAxis() != null) {
            chartMonthlyTrends.getYAxis().setLabel("Amount (" + getActiveCurrencySymbol() + ")");
        }

        // Spending PieChart
        List<CategorySpendingShare> shares = analyticsProcessor.getTopSpendingCategories(activeUser.getId(), 6);
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (CategorySpendingShare s : shares) {
            pieData.add(new PieChart.Data(s.category().getDisplayName(), s.totalSpent().doubleValue()));
        }
        chartCategoryPie.setData(pieData);

        // Monthly Trends BarChart (Actual Spent vs Projected Run-Rate)
        List<CategoryRunRate> runRates = analyticsProcessor.computeCategoryRunRates(activeUser.getId());
        chartMonthlyTrends.getData().clear();

        XYChart.Series<String, Number> actualSeries = new XYChart.Series<>();
        actualSeries.setName("Actual Spent");

        XYChart.Series<String, Number> projectedSeries = new XYChart.Series<>();
        projectedSeries.setName("Projected Run-Rate");

        int count = 0;
        for (CategoryRunRate rr : runRates) {
            if (count++ >= 5) break; // top 5
            String catName = rr.category().getDisplayName();
            actualSeries.getData().add(new XYChart.Data<>(catName, rr.currentSpent()));
            projectedSeries.getData().add(new XYChart.Data<>(catName, rr.projectedMonthlySpend()));
        }
        chartMonthlyTrends.getData().addAll(actualSeries, projectedSeries);

        // Recent 5 transactions
        List<Transaction> allTx = storageEngine.getTransactionsByUserId(activeUser.getId());
        tableDashboardRecent.setItems(FXCollections.observableArrayList(
                allTx.stream().limit(5).toList()
        ));
        tableDashboardRecent.refresh();
    }

    // =========================================================================
    // TAB 2: ADD TRANSACTION
    // =========================================================================

    private void setupAddTransactionForm() {
        comboAddTxType.setItems(FXCollections.observableArrayList(TransactionType.values()));
        comboAddTxType.setValue(TransactionType.EXPENSE);

        comboAddTxCategory.setItems(FXCollections.observableArrayList(Category.values()));
        comboAddTxPaymentMode.setItems(FXCollections.observableArrayList(PaymentMode.values()));
        pickerAddTxDate.setValue(LocalDate.now());

        if (comboAddTxCurrency != null) {
            comboAddTxCurrency.setItems(FXCollections.observableArrayList("USD", "EUR", "GBP", "INR", "CAD", "AUD", "JPY"));
            comboAddTxCurrency.setValue(getBaseCurrency());
        }

        // Category selection auto-assigns transaction type
        comboAddTxCategory.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                comboAddTxType.setValue(newV.getDefaultType());
            }
        });
    }

    @FXML
    public void handleSaveTransaction() {
        lblAddTxError.setText("");

        Category category = comboAddTxCategory.getValue();
        if (category == null) {
            lblAddTxError.setText("Please select a transaction category.");
            return;
        }

        String amtText = txtAddTxAmount.getText();
        if (amtText == null || amtText.isBlank()) {
            lblAddTxError.setText("Monetary amount cannot be empty.");
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amtText.trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                lblAddTxError.setText("Amount must be strictly positive (> 0.00).");
                return;
            }
        } catch (NumberFormatException e) {
            lblAddTxError.setText("Please enter a valid numeric monetary amount (e.g., 54.20).");
            return;
        }

        String txCurrency = (comboAddTxCurrency != null && comboAddTxCurrency.getValue() != null)
                ? comboAddTxCurrency.getValue()
                : getBaseCurrency();

        PaymentMode paymentMode = comboAddTxPaymentMode.getValue();
        if (paymentMode == null) {
            lblAddTxError.setText("Please select a payment channel.");
            return;
        }

        LocalDate date = pickerAddTxDate.getValue();
        if (date == null) {
            lblAddTxError.setText("Please select a transaction date.");
            return;
        }
        LocalDateTime timestamp = date.atTime(LocalTime.now());
        TransactionType type = comboAddTxType.getValue();
        String note = (txtAddTxNote.getText() == null) ? "" : txtAddTxNote.getText().trim();

        try {
            Transaction tx = new Transaction(
                    UUID.randomUUID().toString(), activeUser.getId(), timestamp,
                    category, amount, txCurrency, paymentMode, type, note
            );
            storageEngine.saveTransaction(tx);

            // Re-sync master lists & UI
            masterTransactions.add(0, tx);
            handleClearAddTxForm();

            showInfo("Transaction Ingested", "Successfully recorded " + type.getDisplayName() + " of " + CurrencyFormatter.format(amount, txCurrency));
            refreshAllData();
        } catch (ValidationException | StorageException e) {
            lblAddTxError.setText(e.getMessage());
        }
    }

    @FXML
    public void handleClearAddTxForm() {
        txtAddTxAmount.clear();
        txtAddTxNote.clear();
        comboAddTxCategory.getSelectionModel().clearSelection();
        comboAddTxPaymentMode.getSelectionModel().clearSelection();
        if (comboAddTxCurrency != null) {
            comboAddTxCurrency.setValue(getBaseCurrency());
        }
        comboAddTxType.setValue(TransactionType.EXPENSE);
        pickerAddTxDate.setValue(LocalDate.now());
        lblAddTxError.setText("");
    }

    // =========================================================================
    // TAB 3: TRANSACTIONS LEDGER
    // =========================================================================

    private void setupTransactionsLedger() {
        tableTransactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colTxDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTimestamp().format(DT_FORMATTER)));
        colTxType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType().getDisplayName()));
        colTxCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory().getDisplayName()));
        colTxAmount.setCellValueFactory(c -> {
            Transaction t = c.getValue();
            String repCurr = getReportingCurrency();
            if (t.getCurrency() != null && !t.getCurrency().equalsIgnoreCase(repCurr) && conversionService != null) {
                try {
                    BigDecimal converted = conversionService.convert(t.getAmount(), t.getCurrency(), repCurr);
                    String orig = CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), t.getCurrency());
                    String conv = CurrencyFormatter.format(converted, repCurr);
                    return new SimpleStringProperty(orig + " (~" + conv + ")");
                } catch (Exception e) {
                    return new SimpleStringProperty(CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), t.getCurrency()));
                }
            }
            return new SimpleStringProperty(CurrencyFormatter.formatTransactionAmount(t.getAmount(), t.getType(), repCurr));
        });
        colTxAmount.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colTxPaymentMode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPaymentMode().getDisplayName()));
        colTxNote.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNote()));

        filteredTransactions = new FilteredList<>(masterTransactions, p -> true);

        comboTxTypeFilter.setItems(FXCollections.observableArrayList("ALL", "EXPENSE", "INCOME"));
        comboTxTypeFilter.setValue("ALL");

        ObservableList<Category> catOptions = FXCollections.observableArrayList();
        catOptions.add(null);
        catOptions.addAll(Category.values());
        comboTxCategoryFilter.setItems(catOptions);

        txtTxSearch.textProperty().addListener((obs, oldV, newV) -> applyTxFilters());
        comboTxTypeFilter.valueProperty().addListener((obs, oldV, newV) -> applyTxFilters());
        comboTxCategoryFilter.valueProperty().addListener((obs, oldV, newV) -> applyTxFilters());

        SortedList<Transaction> sorted = new SortedList<>(filteredTransactions);
        sorted.comparatorProperty().bind(tableTransactions.comparatorProperty());
        tableTransactions.setItems(sorted);
    }

    private void applyTxFilters() {
        String keyword = txtTxSearch.getText() == null ? "" : txtTxSearch.getText().trim().toLowerCase();
        String typeFilter = comboTxTypeFilter.getValue();
        Category catFilter = comboTxCategoryFilter.getValue();

        filteredTransactions.setPredicate(tx -> {
            if (typeFilter != null && !typeFilter.equals("ALL") && !tx.getType().name().equalsIgnoreCase(typeFilter)) {
                return false;
            }
            if (catFilter != null && tx.getCategory() != catFilter) {
                return false;
            }
            if (!keyword.isEmpty()) {
                boolean matchNote = tx.getNote() != null && tx.getNote().toLowerCase().contains(keyword);
                boolean matchCat = tx.getCategory().getDisplayName().toLowerCase().contains(keyword);
                boolean matchAmt = tx.getAmount().toPlainString().contains(keyword);
                return matchNote || matchCat || matchAmt;
            }
            return true;
        });
    }

    @FXML
    public void handleResetTxFilters() {
        txtTxSearch.clear();
        comboTxTypeFilter.setValue("ALL");
        comboTxCategoryFilter.setValue(null);
    }

    @FXML
    public void handleEditSelectedTransaction() {
        Transaction selected = tableTransactions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Selection Required", "Select a transaction from the table to edit.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.getNote());
        dialog.setTitle("Edit Transaction Note");
        dialog.setHeaderText("Update memo for " + selected.getCategory().getDisplayName() + " (" + CurrencyFormatter.format(selected.getAmount(), getActiveCurrency()) + ")");
        dialog.setContentText("Note / Memo:");

        dialog.showAndWait().ifPresent(newNote -> {
            selected.setNote(newNote);
            storageEngine.updateTransaction(selected);
            refreshAllData();
        });
    }

    @FXML
    public void handleDeleteSelectedTransaction() {
        Transaction selected = tableTransactions.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Selection Required", "Select a transaction from the table to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Transaction Record");
        confirm.setContentText("Are you sure you want to permanently delete this transaction?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            storageEngine.deleteTransaction(selected.getId());
            masterTransactions.remove(selected);
            refreshAllData();
        }
    }

    private void refreshTransactionsList() {
        if (storageEngine == null || activeUser == null) return;
        masterTransactions.clear();
        masterTransactions.addAll(storageEngine.getTransactionsByUserId(activeUser.getId()));
    }

    // =========================================================================
    // TAB 4: BUDGETS & COMPLIANCE
    // =========================================================================

    private void setupBudgetsView() {
        comboBudgetCategory.setItems(FXCollections.observableArrayList(Category.values()));
        YearMonth now = YearMonth.now();
        spinnerBudgetMonth.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, now.getMonthValue()));
        spinnerBudgetYear.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2020, 2100, now.getYear()));

        spinnerBudgetMonth.valueProperty().addListener((obs, o, n) -> refreshBudgets());
        spinnerBudgetYear.valueProperty().addListener((obs, o, n) -> refreshBudgets());

        tableBudgets.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colBudgetCategory.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category().getDisplayName()));
        colBudgetLimit.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().monthlyLimit(), getActiveCurrency())));
        colBudgetLimit.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colBudgetSpent.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().spentAmount(), getActiveCurrency())));
        colBudgetSpent.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colBudgetRemaining.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().remainingAmount(), getActiveCurrency())));
        colBudgetRemaining.setComparator(CurrencyFormatter::compareFormattedAmounts);

        // Dynamic CSS Color-Coded Progress Bar: Green (<75%), Amber/Orange (75-100%), Red (>100%)
        colBudgetProgress.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().percentageUsed() / 100.0).asObject());
        colBudgetProgress.setCellFactory(col -> new TableCell<CategoryBudgetReport, Double>() {
            private final ProgressBar bar = new ProgressBar();

            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    bar.setProgress(Math.min(1.0, progress));
                    bar.setMaxWidth(Double.MAX_VALUE);
                    bar.getStyleClass().removeAll("progress-bar-green", "progress-bar-orange", "progress-bar-red");

                    if (progress < 0.75) {
                        bar.getStyleClass().add("progress-bar-green");
                    } else if (progress <= 1.0) {
                        bar.getStyleClass().add("progress-bar-orange");
                    } else {
                        bar.getStyleClass().add("progress-bar-red");
                    }
                    setGraphic(bar);
                }
            }
        });

        // Styled Status Tag
        colBudgetStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));
        colBudgetStatus.setCellFactory(col -> new TableCell<CategoryBudgetReport, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().removeAll("badge-ok", "badge-warning", "badge-exceeded");
                    switch (status) {
                        case "OK" -> badge.getStyleClass().add("badge-ok");
                        case "WARNING" -> badge.getStyleClass().add("badge-warning");
                        default -> badge.getStyleClass().add("badge-exceeded");
                    }
                    setGraphic(badge);
                }
            }
        });

        tableBudgets.setItems(masterBudgets);
    }

    @FXML
    public void handleSaveBudgetLimit() {
        lblBudgetError.setText("");

        Category category = comboBudgetCategory.getValue();
        if (category == null) {
            lblBudgetError.setText("Please select a budget category.");
            return;
        }

        String limitText = txtBudgetLimit.getText();
        if (limitText == null || limitText.isBlank()) {
            lblBudgetError.setText("Limit amount cannot be blank.");
            return;
        }

        try {
            BigDecimal limit = new BigDecimal(limitText.trim());
            if (limit.compareTo(BigDecimal.ZERO) <= 0) {
                lblBudgetError.setText("Limit must be strictly positive (> 0.00).");
                return;
            }

            int month = spinnerBudgetMonth.getValue();
            int year = spinnerBudgetYear.getValue();

            budgetManager.setCategoryBudget(activeUser.getId(), category, limit, month, year);
            txtBudgetLimit.clear();
            showInfo("Budget Configured", "Set " + CurrencyFormatter.format(limit, getActiveCurrency()) + " limit for " + category.getDisplayName());
            refreshAllData();
        } catch (NumberFormatException e) {
            lblBudgetError.setText("Please enter a valid numeric limit amount.");
        } catch (ValidationException | StorageException e) {
            lblBudgetError.setText(e.getMessage());
        }
    }

    @FXML
    public void handleDeleteSelectedBudget() {
        CategoryBudgetReport selected = tableBudgets.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Selection Required", "Select a budget entry from the table to delete.");
            return;
        }

        int month = spinnerBudgetMonth.getValue();
        int year = spinnerBudgetYear.getValue();

        Optional<Budget> b = budgetManager.getCategoryBudget(activeUser.getId(), selected.category(), month, year);
        b.ifPresent(budget -> {
            budgetManager.deleteBudget(budget.getId());
            refreshAllData();
        });
    }

    private void refreshBudgets() {
        if (budgetManager == null || activeUser == null) return;
        int m = spinnerBudgetMonth.getValue() != null ? spinnerBudgetMonth.getValue() : YearMonth.now().getMonthValue();
        int y = spinnerBudgetYear.getValue() != null ? spinnerBudgetYear.getValue() : YearMonth.now().getYear();

        masterBudgets.clear();
        masterBudgets.addAll(budgetManager.getAllCategoryBudgetReports(activeUser.getId(), m, y));
    }

    // =========================================================================
    // TAB 5: MILESTONE GOALS
    // =========================================================================

    private void setupGoalsView() {
        tableGoals.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        pickerGoalDate.setValue(LocalDate.now().plusMonths(6));

        colGoalName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colGoalTarget.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getTargetAmount(), getActiveCurrency())));
        colGoalTarget.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colGoalCurrent.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getCurrentAmount(), getActiveCurrency())));
        colGoalCurrent.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colGoalProgress.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getProgressPercentage() / 100.0).asObject());
        colGoalProgress.setCellFactory(col -> new TableCell<Goal, Double>() {
            private final ProgressBar bar = new ProgressBar();
            private final Label pctLabel = new Label();
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(6, bar, pctLabel);
            {
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setStyle("-fx-accent: #0284c7;");
                javafx.scene.layout.HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                pctLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569; -fx-min-width: 42px;");
            }
            @Override
            protected void updateItem(Double progress, boolean empty) {
                super.updateItem(progress, empty);
                if (empty || progress == null) {
                    setGraphic(null);
                } else {
                    bar.setProgress(Math.min(1.0, progress));
                    pctLabel.setText(String.format("%.1f%%", progress * 100.0));
                    setGraphic(box);
                }
            }
        });
        colGoalDeadline.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTargetDate().toString()));

        tableGoals.setItems(masterGoals);
    }

    @FXML
    public void handleCreateGoal() {
        lblGoalError.setText("");

        String name = txtGoalName.getText();
        if (name == null || name.isBlank()) {
            lblGoalError.setText("Goal title cannot be empty.");
            return;
        }

        String targetText = txtGoalTarget.getText();
        if (targetText == null || targetText.isBlank()) {
            lblGoalError.setText("Target amount cannot be empty.");
            return;
        }

        LocalDate deadline = pickerGoalDate.getValue();
        if (deadline == null) {
            lblGoalError.setText("Please select a target deadline.");
            return;
        }

        try {
            BigDecimal target = new BigDecimal(targetText.trim());
            if (target.compareTo(BigDecimal.ZERO) <= 0) {
                lblGoalError.setText("Target amount must be strictly positive.");
                return;
            }

            budgetManager.createGoal(activeUser.getId(), name.trim(), target, deadline);
            txtGoalName.clear();
            txtGoalTarget.clear();
            refreshAllData();
            showInfo("Goal Provisioned", "Created goal: " + name);
        } catch (NumberFormatException e) {
            lblGoalError.setText("Enter a valid numeric target amount.");
        } catch (ValidationException | StorageException e) {
            lblGoalError.setText(e.getMessage());
        }
    }

    @FXML
    public void handleContributeToGoal() {
        Goal selected = tableGoals.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Selection Required", "Select a milestone goal from the table to allocate savings.");
            return;
        }

        String text = txtGoalContribution.getText();
        if (text == null || text.isBlank()) {
            showWarning("Amount Required", "Enter a contribution amount.");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(text.trim());
            budgetManager.contributeToGoal(selected.getId(), amount);
            txtGoalContribution.clear();
            refreshAllData();
            showInfo("Savings Allocated", "Contributed " + CurrencyFormatter.format(amount, getActiveCurrency()) + " toward '" + selected.getName() + "'.");
        } catch (NumberFormatException e) {
            showWarning("Invalid Format", "Enter a valid numeric contribution amount.");
        }
    }

    @FXML
    public void handleDeleteGoal() {
        Goal selected = tableGoals.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Selection Required", "Select a milestone goal to delete.");
            return;
        }

        budgetManager.deleteGoal(selected.getId());
        refreshAllData();
    }

    private void refreshGoals() {
        if (budgetManager == null || activeUser == null) return;
        masterGoals.clear();
        masterGoals.addAll(budgetManager.getUserGoals(activeUser.getId()));
    }

    // =========================================================================
    // TAB 6: REPORTS & FORECASTING
    // =========================================================================

    private void setupReportsView() {
        tableForecast.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        comboVelocityDays.setItems(FXCollections.observableArrayList(7, 14, 30, 60, 90));
        comboVelocityDays.setValue(30);
        comboVelocityDays.valueProperty().addListener((obs, o, n) -> refreshReports());

        colForecastPeriod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().period().toString()));
        colForecastIncome.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().projectedIncome(), getActiveCurrency())));
        colForecastIncome.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colForecastExpense.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().projectedExpense(), getActiveCurrency())));
        colForecastExpense.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colForecastNet.setCellValueFactory(c -> {
            BigDecimal net = c.getValue().projectedNetSavings();
            return new SimpleStringProperty(CurrencyFormatter.formatSignedBalance(net, getActiveCurrency()));
        });
        colForecastNet.setComparator(CurrencyFormatter::compareFormattedAmounts);
        colForecastBalance.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().projectedEndingBalance(), getActiveCurrency())));
        colForecastBalance.setComparator(CurrencyFormatter::compareFormattedAmounts);

        tableForecast.setItems(masterForecasts);
    }

    private void refreshReports() {
        if (analyticsProcessor == null || activeUser == null) return;

        // 1. Velocity
        int window = comboVelocityDays.getValue() != null ? comboVelocityDays.getValue() : 30;
        VelocityMetrics vm = analyticsProcessor.computeVelocity(activeUser.getId(), window);
        String curr = getActiveCurrency();
        lblReportDailySpend.setText(String.format("%s/day", CurrencyFormatter.format(vm.dailySpend(), curr)));
        lblReportDailyTx.setText(String.format("%.2f tx/day", vm.dailyTransactionCount()));
        lblReportWeeklySpend.setText(String.format("%s/wk", CurrencyFormatter.format(vm.weeklySpend(), curr)));
        lblReportWeeklyTx.setText(String.format("%.1f tx/wk", vm.weeklyTransactionCount()));

        // 2. Financial Health Summary
        HealthSummary hs = analyticsProcessor.computeHealthSummary(activeUser.getId());
        lblReportHealthScore.setText(hs.budgetComplianceScore() + " / 100");
        lblReportHealthTier.setText(hs.healthRating());
        progReportHealth.setProgress(hs.budgetComplianceScore() / 100.0);
        lblReportHealthInsight.setText(hs.actionableInsight());

        switch (hs.healthRating()) {
            case "EXCELLENT" -> lblReportHealthTier.setStyle("-fx-text-fill: #16a34a;");
            case "HEALTHY" -> lblReportHealthTier.setStyle("-fx-text-fill: #0284c7;");
            case "MODERATE" -> lblReportHealthTier.setStyle("-fx-text-fill: #f59e0b;");
            default -> lblReportHealthTier.setStyle("-fx-text-fill: #dc2626;");
        }

        // 3. Balance Forecast
        ForecastResult fr = analyticsProcessor.computeBalanceForecast(activeUser.getId(), 3);
        lblReportMonthEndForecast.setText("Projected Month-End Balance: " + CurrencyFormatter.format(fr.projectedCurrentMonthEndBalance(), curr));

        masterForecasts.clear();
        masterForecasts.addAll(fr.futureForecasts());
    }

    // =========================================================================
    // TAB 7: PROFILE & DATA INTERCHANGE
    // =========================================================================

    private void setupProfileView() {
        ObservableList<String> currencies = FXCollections.observableArrayList("USD", "EUR", "GBP", "INR", "CAD", "AUD", "JPY");
        comboProfileCurrency.setItems(currencies);
        if (comboProfileReportingCurrency != null) {
            comboProfileReportingCurrency.setItems(currencies);
        }
        populateProfileForm();
    }

    private void populateProfileForm() {
        if (activeUser == null) return;
        txtProfileName.setText(activeUser.getName());
        txtProfileEmail.setText(activeUser.getEmail());
        comboProfileCurrency.setValue(activeUser.getCurrency());
        if (comboProfileReportingCurrency != null) {
            comboProfileReportingCurrency.setValue(activeUser.getReportingCurrency());
        }
        txtProfileIncome.setText(activeUser.getMonthlyIncome().toPlainString());
        txtProfileGlobalBudget.setText(activeUser.getGlobalBudget().toPlainString());
        txtProfileSavingsGoal.setText(activeUser.getSavingsGoal().toPlainString());
        lblProfileStatus.setText("");
        updateExchangeRateStatusLabel();
    }

    @FXML
    public void handleSaveProfile() {
        lblProfileStatus.setText("");
        try {
            String name = txtProfileName.getText();
            String email = txtProfileEmail.getText();
            String currency = comboProfileCurrency.getValue();
            String reportingCurrency = (comboProfileReportingCurrency != null && comboProfileReportingCurrency.getValue() != null)
                    ? comboProfileReportingCurrency.getValue()
                    : currency;

            BigDecimal income = parseMoney(txtProfileIncome.getText(), "Monthly Income");
            BigDecimal budget = parseMoney(txtProfileGlobalBudget.getText(), "Global Budget");
            BigDecimal goal = parseMoney(txtProfileSavingsGoal.getText(), "Savings Goal");

            activeUser.setName(name);
            activeUser.setEmail(email);
            activeUser.setCurrency(currency);
            activeUser.setReportingCurrency(reportingCurrency);
            activeUser.setMonthlyIncome(income);
            activeUser.setGlobalBudget(budget);
            activeUser.setSavingsGoal(goal);

            storageEngine.updateUser(activeUser);
            updateSidebarUserInfo();
            updateExchangeRateStatusLabel();
            refreshAllData();
            showInfo("Profile Saved", "User configuration updated successfully.");
        } catch (ValidationException | StorageException e) {
            lblProfileStatus.setText(e.getMessage());
        }
    }

    @FXML
    public void handleRefreshExchangeRates() {
        if (conversionService == null || activeUser == null) return;
        try {
            String base = getBaseCurrency();
            conversionService.refreshRates(base);
            showInfo("Rates Refreshed", "Successfully fetched latest exchange rates for " + base + ".");
            updateExchangeRateStatusLabel();
            refreshAllData();
        } catch (Exception e) {
            showWarning("Rate Refresh Notice", "Could not fetch online live rates: " + e.getMessage() + ". Using cached rates.");
            updateExchangeRateStatusLabel();
        }
    }

    private void updateExchangeRateStatusLabel() {
        if (lblExchangeRateStatus == null || conversionService == null || activeUser == null) return;
        String base = getBaseCurrency();
        String rep = getReportingCurrency();
        try {
            if (base.equalsIgnoreCase(rep)) {
                String quote = base.equalsIgnoreCase("USD") ? "EUR" : "USD";
                var rate = conversionService.getExchangeRate(base, quote);
                lblExchangeRateStatus.setText(String.format("1 %s = %s %s (Source: %s, Updated: %s)",
                        base, rate.getRate().stripTrailingZeros().toPlainString(), quote,
                        rate.getProvider(), rate.getTimestamp().format(DT_FORMATTER)));
            } else {
                var rate = conversionService.getExchangeRate(base, rep);
                lblExchangeRateStatus.setText(String.format("1 %s = %s %s (Source: %s, Updated: %s)",
                        base, rate.getRate().stripTrailingZeros().toPlainString(), rep,
                        rate.getProvider(), rate.getTimestamp().format(DT_FORMATTER)));
            }
        } catch (Exception e) {
            lblExchangeRateStatus.setText("Exchange Rates: Cached/Offline (" + e.getMessage() + ")");
        }
    }

    @FXML
    public void handleExportToCsv() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Target Folder to Export FinTrack Data");
        Window window = mainTabPane.getScene() != null ? mainTabPane.getScene().getWindow() : null;
        File dir = chooser.showDialog(window);

        if (dir != null) {
            try {
                storageEngine.exportToCsv(dir.getAbsolutePath());
                showInfo("Export Complete", "Successfully exported records to: " + dir.getAbsolutePath());
            } catch (StorageException e) {
                showError("Export Error", "CSV export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleImportFromCsv() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder with CSV Backups to Import");
        Window window = mainTabPane.getScene() != null ? mainTabPane.getScene().getWindow() : null;
        File dir = chooser.showDialog(window);

        if (dir != null) {
            try {
                storageEngine.importFromCsv(dir.getAbsolutePath());
                refreshAllData();
                showInfo("Import Complete", "CSV data successfully imported into SQLite.");
            } catch (StorageException e) {
                showError("Import Error", "CSV import failed: " + e.getMessage());
            }
        }
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

    // =========================================================================
    // GENERAL REFRESH & ALERTS
    // =========================================================================

    public void refreshAllData() {
        refreshTransactionsList();
        refreshBudgets();
        refreshGoals();
        refreshReports();
        refreshDashboard();
        populateProfileForm();
        updateDynamicCurrencyLabels();
    }

    private void updateDynamicCurrencyLabels() {
        String symbol = getActiveCurrencySymbol();
        if (colDashAmount != null) colDashAmount.setText("Amount (" + symbol + ")");
        if (colTxAmount != null) colTxAmount.setText("Amount (" + symbol + ")");
        if (colBudgetLimit != null) colBudgetLimit.setText("Allocated Limit (" + symbol + ")");
        if (colBudgetSpent != null) colBudgetSpent.setText("Actual Spent (" + symbol + ")");
        if (colBudgetRemaining != null) colBudgetRemaining.setText("Remaining (" + symbol + ")");
        if (colGoalTarget != null) colGoalTarget.setText("Target (" + symbol + ")");
        if (colGoalCurrent != null) colGoalCurrent.setText("Current Saved (" + symbol + ")");
        if (colForecastIncome != null) colForecastIncome.setText("Estimated Income (" + symbol + ")");
        if (colForecastExpense != null) colForecastExpense.setText("Projected Outflow (" + symbol + ")");
        if (colForecastNet != null) colForecastNet.setText("Projected Net (" + symbol + ")");
        if (colForecastBalance != null) colForecastBalance.setText("Projected Balance (" + symbol + ")");

        if (txtGoalContribution != null) txtGoalContribution.setPromptText("Contribution amount (" + symbol + ")");
        if (chartMonthlyTrends != null && chartMonthlyTrends.getYAxis() != null) {
            chartMonthlyTrends.getYAxis().setLabel("Amount (" + symbol + ")");
        }

        if (tableTransactions != null) tableTransactions.refresh();
        if (tableDashboardRecent != null) tableDashboardRecent.refresh();
        if (tableBudgets != null) tableBudgets.refresh();
        if (tableGoals != null) tableGoals.refresh();
        if (tableForecast != null) tableForecast.refresh();
    }

    private void showInfo(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private void showWarning(String title, String content) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private void showError(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
