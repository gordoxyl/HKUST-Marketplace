package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.AuthService;
import com.hkust.marketplace.services.LoginResult;
import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class BuyerLoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // UC2 Log In: checks and session handling are shared by all portals
        LoginResult result = new AuthService().login(
                usernameField.getText(), passwordField.getText(), UserRole.BUYER);
        if (result != LoginResult.SUCCESS) {
            showError(result.getMessage());
            return;
        }
        SceneManager.showBuyerDashboard();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    @FXML
    private void goRegister() {
        SceneManager.showBuyerRegister();
    }

    @FXML
    private void goWelcome() {
        SceneManager.showWelcome();
    }
}
