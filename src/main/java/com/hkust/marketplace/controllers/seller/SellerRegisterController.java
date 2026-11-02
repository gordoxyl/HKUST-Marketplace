package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.services.AuthService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class SellerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<MeetupLocation> meetupCombo;
    @FXML private TextArea bioArea;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    @FXML
    public void initialize() {
        meetupCombo.setItems(FXCollections.observableArrayList(MeetupLocation.values()));
    }

    @FXML
    private void handleRegister() {
        hideMessages();
        List<MeetupLocation> locations = meetupCombo.getValue() == null ? List.of() : List.of(meetupCombo.getValue());

        // Register as Seller: same validation and hashed storage as buyers (UC1)
        String error = new AuthService().registerSeller(usernameField.getText().trim(), fullNameField.getText(),
                passwordField.getText(), confirmPasswordField.getText(), bioArea.getText(), locations);
        if (error != null) { showError(error); return; }

        showSuccess("Seller account created! You can now login.");
        usernameField.clear(); fullNameField.clear(); passwordField.clear();
        confirmPasswordField.clear(); meetupCombo.getSelectionModel().clearSelection(); bioArea.clear();
    }

    private void showError(String msg) {
        errorLabel.setText(msg); errorLabel.setVisible(true); errorLabel.setManaged(true);
    }
    private void showSuccess(String msg) {
        successLabel.setText(msg); successLabel.setVisible(true); successLabel.setManaged(true);
    }
    private void hideMessages() {
        errorLabel.setVisible(false); errorLabel.setManaged(false);
        successLabel.setVisible(false); successLabel.setManaged(false);
    }

    @FXML private void goLogin() { SceneManager.showSellerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
