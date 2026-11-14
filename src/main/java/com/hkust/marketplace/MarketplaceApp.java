package com.hkust.marketplace;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.services.BorrowingDeadlineService;
import com.hkust.marketplace.services.BorrowingDeadlineTimer;
import com.hkust.marketplace.ui.LiveUpdates;
import com.hkust.marketplace.utils.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * HKUST Marketplace - Main Application Entry Point
 *
 * This application provides the Buyer, Seller and Admin portals. All data is kept in
 * DataStore, which saves it to an encrypted file after every change and loads it here
 * at start-up. While it runs, the borrowing-deadline timer (UC12) sends due-date reminders and
 * returns borrowed items whose period has ended, whichever screen is open.
 */
public class MarketplaceApp extends Application {

    private BorrowingDeadlineTimer deadlineTimer;

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

        // UC12: the Timer actor. Its first check runs at once, so items that became due while
        // the app was closed are returned now. A change is shown on the open screen (step 4).
        deadlineTimer = new BorrowingDeadlineTimer(new BorrowingDeadlineService(store),
                BorrowingDeadlineTimer.CHECK_EVERY, report -> Platform.runLater(LiveUpdates::dataChanged));
        deadlineTimer.start();

        if (store.getLoadProblem() != null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, store.getLoadProblem());
            alert.setTitle("Saved data not loaded");
            alert.setHeaderText("The saved data could not be read");
            alert.showAndWait();
        }
    }

    /** The app is closing: the timer stops after the check it may be running, so that check is saved. */
    @Override
    public void stop() {
        if (deadlineTimer != null) {
            deadlineTimer.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
