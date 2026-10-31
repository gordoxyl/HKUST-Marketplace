package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.AuthService;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SellerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> meetupCombo;
    @FXML private TextArea bioArea;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    @FXML
    public void initialize() {
        meetupCombo.setItems(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
    }

    @FXML
    private void handleRegister() {
        hideMessages();
        String username = usernameField.getText().trim();

        // Register as Seller: same validation and hashed storage as buyers (UC1)
        String error = new AuthService().register(username, fullNameField.getText(),
                passwordField.getText(), confirmPasswordField.getText(), UserRole.SELLER);
        if (error != null) { showError(error); return; }

        User user = DummyDataService.getInstance().findUser(username);
        if (meetupCombo.getValue() != null) user.setPreferredMeetup(meetupCombo.getValue());
        if (bioArea.getText() != null && !bioArea.getText().isBlank()) user.setBio(bioArea.getText().trim());

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
