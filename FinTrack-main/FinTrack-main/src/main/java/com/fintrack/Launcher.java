package com.fintrack;

/**
 * Bootstrap launcher class that decouples the main entry point from JavaFX Application inheritance,
 * ensuring seamless execution from Uber-JARs and IDE classpath environments without module-path errors.
 */
public class Launcher {

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
