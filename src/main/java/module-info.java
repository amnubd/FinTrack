module com.fintrack {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires com.opencsv;

    opens com.fintrack to javafx.fxml, javafx.graphics;
    opens com.fintrack.controller to javafx.fxml;
    opens com.fintrack.model to javafx.base;
    opens com.fintrack.service to javafx.base;
    opens com.fintrack.service.dto to javafx.base;

    exports com.fintrack;
    exports com.fintrack.controller;
    exports com.fintrack.model;
    exports com.fintrack.service;
    exports com.fintrack.service.dto;
    exports com.fintrack.storage;
    exports com.fintrack.util;
}
