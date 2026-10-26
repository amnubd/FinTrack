package com.fintrack;

import com.fintrack.controller.MainController;
import com.fintrack.service.AnalyticsProcessor;
import com.fintrack.service.BudgetManager;
import com.fintrack.storage.SQLiteStorageEngine;
import com.fintrack.storage.StorageEngine;
import com.fintrack.util.SeedData;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Main application entry point for FinTrack.
 * Initializes the persistence storage engine, business services, executes initial data seeding
 * for demo accounts if empty, binds controllers, and configures the primary JavaFX stage.
 */
public class MainApp extends Application {

    private StorageEngine storageEngine;

    @Override
    public void init() throws Exception {
        // 1. Initialize SQLite Database Engine
        storageEngine = new SQLiteStorageEngine("fintrack.db");

        // 2. Pre-seed 3 months of realistic INR sample transactions if running on fresh DB
        SeedData.seedIfEmpty(storageEngine);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxmlLocation = getClass().getResource("/com/fintrack/fxml/MainView.fxml");
        if (fxmlLocation == null) {
            throw new IllegalStateException("Cannot locate MainView.fxml at /com/fintrack/fxml/MainView.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);

        // Inject storageEngine into MainController
        loader.setControllerFactory(controllerClass -> {
            if (controllerClass == MainController.class) {
                return new MainController(storageEngine);
            }
            try {
                return controllerClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to construct controller: " + controllerClass, e);
            }
        });

        Parent root = loader.load();
        Scene scene = new Scene(root, 1180, 780);

        URL cssLocation = getClass().getResource("/com/fintrack/css/styles.css");
        if (cssLocation != null) {
            scene.getStylesheets().add(cssLocation.toExternalForm());
        }

        primaryStage.setTitle("FinTrack — Automated Personal Financial Management Infrastructure");
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(680);
        primaryStage.setScene(scene);

        // Safe resource disposal on window exit
        primaryStage.setOnCloseRequest(event -> {
            if (storageEngine != null) {
                storageEngine.close();
            }
        });

        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        if (storageEngine != null) {
            storageEngine.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
