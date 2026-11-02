package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.services.AuthService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class BuyerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> residenceCombo;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    @FXML
    public void initialize() {
        residenceCombo.setItems(FXCollections.observableArrayList(Buyer.RESIDENCE_OPTIONS));
    }

    @FXML
    private void handleRegister() {
        hideMessages();

        // UC1 Register as Buyer: validation and hashed storage are shared with sellers
        String error = new AuthService().registerBuyer(usernameField.getText().trim(), fullNameField.getText(),
                passwordField.getText(), confirmPasswordField.getText(), residenceCombo.getValue());
        if (error != null) {
            showError(error);
            return;
        }

        showSuccess("Registration successful! You can now login.");
        // Clear form
        usernameField.clear();
        fullNameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        residenceCombo.getSelectionModel().clearSelection();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showSuccess(String msg) {
        successLabel.setText(msg);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
    }

    private void hideMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }

    @FXML private void goLogin() { SceneManager.showBuyerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
