package com.hkust.marketplace;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.utils.SceneManager;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * HKUST Marketplace - Main Application Entry Point
 *
 * This application provides the Buyer, Seller and Admin portals. All data is kept in
 * DataStore, which saves it to an encrypted file after every change and loads it here
 * at start-up.
 */
public class MarketplaceApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Load the saved data (or create the demo data) before the first screen opens
        DataStore store = DataStore.getInstance();

        SceneManager.init(primaryStage);
        SceneManager.showWelcome();
        primaryStage.setTitle("HKUST Marketplace");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.show();

        if (store.getLoadProblem() != null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, store.getLoadProblem());
            alert.setTitle("Saved data not loaded");
            alert.setHeaderText("The saved data could not be read");
            alert.showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
