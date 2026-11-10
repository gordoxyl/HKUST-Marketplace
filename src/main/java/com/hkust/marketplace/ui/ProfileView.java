package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.services.AccountValidator;
import com.hkust.marketplace.services.ProfileService;
import com.hkust.marketplace.services.ServiceException;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The profile screen, the same in all three portals (UC3 Manage Profile; Tasks 1.7, 2.6,
 * 3.4). Everyone edits their full name and password; a seller also edits the bio and
 * preferred meet-up locations. The username and a buyer's residence hall are shown but
 * cannot be changed.
 * <pre>{@code
 * host.show("Manage Profile", new ProfileView(new ProfileService()));
 * }</pre>
 * All checks are in the tested {@link ProfileService}; this class only shows the form.
 */
public class ProfileView extends ScrollPane {

    private final ProfileService service;
    private final VBox form = new VBox(10);

    public ProfileView(ProfileService service) {
        this.service = service;
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        form.setMaxWidth(480);
        VBox page = new VBox(form);
        page.setPadding(new Insets(4));
        setContent(page);
        setFitToWidth(true);
        fill();
    }

    /** Shows the current profile, discarding any unsaved edits (UC3 step 2 and A4). */
    private void fill() {
        form.getChildren().clear();
        User user;
        try {
            user = service.getMyProfile();
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }

        Label title = new Label("Your profile");
        title.getStyleClass().add("section-title");
        form.getChildren().addAll(title, fixedValue("Username", "@" + user.getUsername()));
        if (user instanceof Buyer buyer) {
            String hall = buyer.getResidenceHall() == null ? "Not given" : buyer.getResidenceHall();
            form.getChildren().add(fixedValue("Residence hall", hall));
        }

        TextField fullName = new TextField(user.getFullName());
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("Leave blank to keep your current password");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Type the new password again");
        Label rules = hint(AccountValidator.PASSWORD_RULES);
        form.getChildren().addAll(
                new Label("Full name"), fullName,
                new Label("New password"), newPassword,
                new Label("Confirm new password"), confirm, rules);

        TextArea bio = null;
        List<CheckBox> locations = new ArrayList<>();
        if (user instanceof Seller seller) {
            bio = new TextArea(seller.getBio() == null ? "" : seller.getBio());
            bio.setPrefRowCount(3);
            bio.setWrapText(true);
            FlowPane choices = new FlowPane(12, 6);
            for (MeetupLocation location : MeetupLocation.values()) {
                CheckBox box = new CheckBox(location.getDisplayName());
                box.setUserData(location);
                box.setSelected(seller.getPreferredMeetupLocations().contains(location));
                locations.add(box);
                choices.getChildren().add(box);
            }
            form.getChildren().addAll(new Label("Bio (optional)"), bio,
                    new Label("Preferred meet-up locations (optional)"), choices);
        }

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-primary");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-outline");
        cancel.setOnAction(e -> fill()); // A4: keep the profile as it was
        TextArea bioField = bio;
        save.setOnAction(e -> save(user, fullName.getText(), newPassword.getText(), confirm.getText(),
                bioField == null ? null : bioField.getText(), locations));
        form.getChildren().add(new HBox(10, save, cancel));
    }

    private void save(User user, String fullName, String newPassword, String confirm,
                      String bio, List<CheckBox> locationBoxes) {
        try {
            if (user instanceof Seller) {
                List<MeetupLocation> chosen = locationBoxes.stream()
                        .filter(CheckBox::isSelected)
                        .map(box -> (MeetupLocation) box.getUserData())
                        .toList();
                service.updateSellerProfile(fullName, newPassword, confirm, bio, chosen);
            } else {
                service.updateProfile(fullName, newPassword, confirm);
            }
        } catch (ServiceException e) {
            Dialogs.problem(e); // A1-A3: the typed values stay so they can be corrected
            return;
        }
        String passwordNote = newPassword.isEmpty() ? "" : " Use your new password the next time you log in.";
        Dialogs.info("Profile updated", "Your profile was updated successfully." + passwordNote);
        fill();
    }

    /** A value that cannot be changed (UC3 Special Requirement 1). */
    private static VBox fixedValue(String name, String value) {
        Label label = new Label(name);
        Label shown = new Label(value);
        shown.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        return new VBox(2, label, shown, hint("This cannot be changed."));
    }

    private static Label hint(String text) {
        Label hint = new Label(text);
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        return hint;
    }
}
