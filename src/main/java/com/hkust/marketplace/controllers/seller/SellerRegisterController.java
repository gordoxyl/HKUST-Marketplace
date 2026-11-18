package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.services.AccountValidator;
import com.hkust.marketplace.services.AuthService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Task 2.1 Seller Registration: creates a seller account from the form.
 *
 * <p>The form asks for a username, full name, password, confirm password, any number of
 * preferred meet-up locations, and an optional bio. All checks (unique username across
 * buyer/seller/admin, password strength, matching passwords) are in the shared, tested
 * {@link AuthService}; this screen only collects the input, shows the service's error
 * message, and clears the form after success.
 *
 * <p><b>Task 2.1 changes</b> (everything else here was already in the original skeleton):
 * the meet-up field is now a set of check boxes, so a seller can pick more than one
 * location (the spec says "location(s)"); the password rules are shown under the password
 * field; and the new {@link #clearForm()} helper resets the form. Validation, hashing,
 * error messages and navigation are unchanged.
 */
public class SellerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private FlowPane meetupChoices;
    @FXML private TextArea bioArea;
    /** Task 2.1: shows the password rules under the password field. */
    @FXML private Label passwordRulesLabel;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    /** Task 2.1: one check box per predefined HKUST location, in the same order every time. */
    private final List<CheckBox> meetupBoxes = new ArrayList<>();

    /** Task 2.1: builds the location check boxes and shows the password rules under the field. */
    @FXML
    public void initialize() {
        passwordRulesLabel.setText(AccountValidator.PASSWORD_RULES);
        for (MeetupLocation location : MeetupLocation.values()) {
            CheckBox box = new CheckBox(location.getDisplayName());
            box.setUserData(location);
            meetupBoxes.add(box);
            meetupChoices.getChildren().add(box);
        }
    }

    /** UC "Register as Seller": validates through AuthService and creates the account. */
    @FXML
    private void handleRegister() {
        hideMessages();
        List<MeetupLocation> locations = selectedMeetupLocations(); // Task 2.1: every ticked location

        String error = new AuthService().registerSeller(usernameField.getText().trim(),
                fullNameField.getText(), passwordField.getText(), confirmPasswordField.getText(),
                bioArea.getText(), locations);
        if (error != null) {
            showError(error);
            return;
        }

        showSuccess("Seller account created! You can now login.");
        clearForm();
    }

    /** Task 2.1: the locations the seller ticked, in the order they appear on the form. */
    private List<MeetupLocation> selectedMeetupLocations() {
        return meetupBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(box -> (MeetupLocation) box.getUserData())
                .toList();
    }

    /** Task 2.1: empties the form after a successful registration (the clears were inline before). */
    private void clearForm() {
        usernameField.clear();
        fullNameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        bioArea.clear();
        meetupBoxes.forEach(box -> box.setSelected(false));
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

    @FXML private void goLogin() { SceneManager.showSellerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
