package com.fintrack.controller;

import com.fintrack.model.Category;
import com.fintrack.service.AnalyticsProcessor;
import com.fintrack.service.dto.CategoryRunRate;
import com.fintrack.service.dto.CategorySpendingShare;
import com.fintrack.service.dto.ForecastResult;
import com.fintrack.service.dto.HealthSummary;
import com.fintrack.service.dto.MonthlyForecast;
import com.fintrack.service.dto.VelocityMetrics;
import com.fintrack.storage.StorageException;
import com.fintrack.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller mediating the presentation of computational outputs:
 * transaction velocity, run-rate burn meters, balance forecasts, and financial health diagnostics.
 */
public class AnalyticsController implements Initializable {

    // --- Velocity Widgets ---
    @FXML private ComboBox<Integer> velocityDaysComboBox;
    @FXML private Label dailyTxLabel;
    @FXML private Label dailySpendLabel;
    @FXML private Label weeklyTxLabel;
    @FXML private Label weeklySpendLabel;
    @FXML private Label totalAnalyzedLabel;

    // --- Balance Forecast Widgets ---
    @FXML private Label currentBalanceLabel;
    @FXML private Label projectedMonthEndBalanceLabel;
    @FXML private TableView<MonthlyForecast> forecastTable;
    @FXML private TableColumn<MonthlyForecast, String> forecastPeriodColumn;
    @FXML private TableColumn<MonthlyForecast, String> forecastIncomeColumn;
    @FXML private TableColumn<MonthlyForecast, String> forecastExpenseColumn;
    @FXML private TableColumn<MonthlyForecast, String> forecastNetColumn;
    @FXML private TableColumn<MonthlyForecast, String> forecastEndingBalanceColumn;

    // --- Category Run-Rates & MoM Table ---
    @FXML private TableView<CategoryRunRate> runRateTable;
    @FXML private TableColumn<CategoryRunRate, String> runRateCategoryColumn;
    @FXML private TableColumn<CategoryRunRate, String> runRateSpentColumn;
    @FXML private TableColumn<CategoryRunRate, String> runRateDailyColumn;
    @FXML private TableColumn<CategoryRunRate, String> runRateProjectedColumn;
    @FXML private TableColumn<CategoryRunRate, String> runRatePrevMonthColumn;
    @FXML private TableColumn<CategoryRunRate, String> runRateMomChangeColumn;

    // --- Top Categories Breakdown ---
    @FXML private PieChart spendingPieChart;
    @FXML private TableView<CategorySpendingShare> topCategoriesTable;
    @FXML private TableColumn<CategorySpendingShare, String> topCatNameColumn;
    @FXML private TableColumn<CategorySpendingShare, String> topCatAmountColumn;
    @FXML private TableColumn<CategorySpendingShare, String> topCatShareColumn;

    // --- Financial Health Diagnostic ---
    @FXML private ProgressBar healthProgressBar;
    @FXML private Label healthScoreLabel;
    @FXML private Label healthRatingLabel;
    @FXML private Label savingsRateLabel;
    @FXML private Label netSavingsLabel;
    @FXML private Label insightLabel;

    @FXML private Button refreshButton;

    private AnalyticsProcessor analyticsProcessor;
    private String currentUserId = "default_user";

    private final ObservableList<MonthlyForecast> forecastList = FXCollections.observableArrayList();
    private final ObservableList<CategoryRunRate> runRateList = FXCollections.observableArrayList();
    private final ObservableList<CategorySpendingShare> topCategoriesList = FXCollections.observableArrayList();
    private final ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

    public AnalyticsController() {}

    public AnalyticsController(AnalyticsProcessor analyticsProcessor, String currentUserId) {
        this.analyticsProcessor = analyticsProcessor;
        this.currentUserId = currentUserId;
    }

    public void setAnalyticsProcessor(AnalyticsProcessor analyticsProcessor) {
        this.analyticsProcessor = analyticsProcessor;
        refreshAnalytics();
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
        refreshAnalytics();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupVelocityControls();
        setupForecastTable();
        setupRunRateTable();
        setupTopCategories();

        if (spendingPieChart != null) {
            spendingPieChart.setData(pieChartData);
        }

        if (analyticsProcessor != null) {
            refreshAnalytics();
        }
    }

    private void setupVelocityControls() {
        if (velocityDaysComboBox != null) {
            velocityDaysComboBox.setItems(FXCollections.observableArrayList(7, 14, 30, 60, 90));
            velocityDaysComboBox.setValue(30);
            velocityDaysComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) refreshVelocity();
            });
        }
    }

    private void setupForecastTable() {
        forecastPeriodColumn.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().period().toString()));

        forecastIncomeColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().projectedIncome(), "USD")));
        forecastIncomeColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        forecastExpenseColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().projectedExpense(), "USD")));
        forecastExpenseColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        forecastNetColumn.setCellValueFactory(d -> {
            var net = d.getValue().projectedNetSavings();
            return new SimpleStringProperty(CurrencyFormatter.formatSignedBalance(net, "USD"));
        });
        forecastNetColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        forecastEndingBalanceColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().projectedEndingBalance(), "USD")));
        forecastEndingBalanceColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        forecastTable.setItems(forecastList);
    }

    private void setupRunRateTable() {
        runRateCategoryColumn.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().category().getDisplayName()));

        runRateSpentColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().currentSpent(), "USD")));
        runRateSpentColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        runRateDailyColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().dailyRunRate(), "USD") + "/day"));
        runRateDailyColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        runRateProjectedColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().projectedMonthlySpend(), "USD")));
        runRateProjectedColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        runRatePrevMonthColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().previousMonthSpent(), "USD")));
        runRatePrevMonthColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        runRateMomChangeColumn.setCellValueFactory(d -> {
            double pct = d.getValue().percentageChange();
            String prefix = pct >= 0 ? "+" : "";
            return new SimpleStringProperty(String.format("%s%.1f%%", prefix, pct));
        });

        // Color coding for MoM change (Red for increased spending, Green for reduced spending)
        runRateMomChangeColumn.setCellFactory(col -> new TableCell<CategoryRunRate, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("+") && !item.equals("+0.0%")) {
                        setStyle("-fx-text-fill: #d32f2f; -fx-font-weight: bold;"); // Red (increased spend)
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;"); // Green (reduced spend)
                    } else {
                        setStyle("-fx-text-fill: #555555;");
                    }
                }
            }
        });

        runRateTable.setItems(runRateList);
    }

    private void setupTopCategories() {
        topCatNameColumn.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().category().getDisplayName()));

        topCatAmountColumn.setCellValueFactory(d ->
                new SimpleStringProperty(CurrencyFormatter.format(d.getValue().totalSpent(), "USD")));
        topCatAmountColumn.setComparator(CurrencyFormatter::compareFormattedAmounts);

        topCatShareColumn.setCellValueFactory(d ->
                new SimpleStringProperty(String.format("%.1f%%", d.getValue().percentageOfTotal())));

        topCategoriesTable.setItems(topCategoriesList);
    }

    @FXML
    public void refreshAnalytics() {
        if (analyticsProcessor == null) return;
        try {
            refreshVelocity();
            refreshForecast();
            refreshRunRates();
            refreshTopCategories();
            refreshHealthSummary();
        } catch (StorageException e) {
            showError("Analytics Computation Failed", "Failed to calculate financial metrics", e.getMessage());
        }
    }

    private void refreshVelocity() {
        int window = (velocityDaysComboBox != null && velocityDaysComboBox.getValue() != null)
                ? velocityDaysComboBox.getValue() : 30;

        VelocityMetrics vm = analyticsProcessor.computeVelocity(currentUserId, window);
        if (dailyTxLabel != null) dailyTxLabel.setText(String.format("%.2f tx/day", vm.dailyTransactionCount()));
        if (dailySpendLabel != null) dailySpendLabel.setText(String.format("$%s/day", vm.dailySpend().toPlainString()));
        if (weeklyTxLabel != null) weeklyTxLabel.setText(String.format("%.1f tx/wk", vm.weeklyTransactionCount()));
        if (weeklySpendLabel != null) weeklySpendLabel.setText(String.format("$%s/wk", vm.weeklySpend().toPlainString()));
        if (totalAnalyzedLabel != null) totalAnalyzedLabel.setText(String.format("%d tx ($%s total)", vm.totalTransactions(), vm.totalSpend().toPlainString()));
    }

    private void refreshForecast() {
        ForecastResult fr = analyticsProcessor.computeBalanceForecast(currentUserId, 3);
        if (currentBalanceLabel != null) {
            currentBalanceLabel.setText("$" + fr.currentBalance().toPlainString());
        }
        if (projectedMonthEndBalanceLabel != null) {
            projectedMonthEndBalanceLabel.setText("$" + fr.projectedCurrentMonthEndBalance().toPlainString());
        }
        forecastList.clear();
        forecastList.addAll(fr.futureForecasts());
    }

    private void refreshRunRates() {
        runRateList.clear();
        runRateList.addAll(analyticsProcessor.computeCategoryRunRates(currentUserId));
    }

    private void refreshTopCategories() {
        List<CategorySpendingShare> topShares = analyticsProcessor.getTopSpendingCategories(currentUserId, 5);
        topCategoriesList.clear();
        topCategoriesList.addAll(topShares);

        pieChartData.clear();
        for (CategorySpendingShare share : topShares) {
            pieChartData.add(new PieChart.Data(share.category().getDisplayName(), share.totalSpent().doubleValue()));
        }
    }

    private void refreshHealthSummary() {
        HealthSummary hs = analyticsProcessor.computeHealthSummary(currentUserId);
        if (healthScoreLabel != null) healthScoreLabel.setText(hs.budgetComplianceScore() + " / 100");
        if (healthProgressBar != null) healthProgressBar.setProgress(hs.budgetComplianceScore() / 100.0);
        if (healthRatingLabel != null) healthRatingLabel.setText(hs.healthRating());
        if (savingsRateLabel != null) savingsRateLabel.setText(String.format("%.1f%%", hs.savingsRate()));
        if (netSavingsLabel != null) {
            String prefix = hs.netSavings().signum() >= 0 ? "+$" : "-$";
            netSavingsLabel.setText(prefix + hs.netSavings().abs().toPlainString());
        }
        if (insightLabel != null) insightLabel.setText(hs.actionableInsight());

        // Color coding for health rating
        if (healthRatingLabel != null) {
            switch (hs.healthRating()) {
                case "EXCELLENT" -> healthRatingLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                case "HEALTHY" -> healthRatingLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
                case "MODERATE" -> healthRatingLabel.setStyle("-fx-text-fill: #f57f17; -fx-font-weight: bold;");
                default -> healthRatingLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
            }
        }
    }

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
