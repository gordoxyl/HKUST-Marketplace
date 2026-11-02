package com.hkust.marketplace.utils;

// DEV-ONLY: this whole class is a testing shortcut. Delete it, and every line marked
// DEV-ONLY that calls it, before the final release.

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.PasswordHasher;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

/**
 * Dev-build shortcut that pre-fills each portal's login form with a test account,
 * so developers do not have to type a username and password on every run.
 *
 * It is off unless the app is started with the Maven dev profile:
 * {@code mvnw clean javafx:run -Pdev}, which sets the system property
 * {@code marketplace.dev=true}. The normal run command never enables it.
 *
 * Only the form is filled in; logging in still goes through AuthService (UC2).
 */
public final class DevLogin {

    /** System property set by the Maven dev profile. */
    public static final String DEV_PROPERTY = "marketplace.dev";

    // Test accounts. The passwords meet the UC1 strength rules.
    private static final String BUYER_USERNAME = "buyer";
    private static final String BUYER_PASSWORD = "Buyer.123";
    private static final String SELLER_USERNAME = "seller";
    private static final String SELLER_PASSWORD = "Seller.123";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin.123";

    private DevLogin() {
        // Utility class
    }

    /** True if the app was started in dev mode. */
    public static boolean isEnabled() {
        return Boolean.getBoolean(DEV_PROPERTY);
    }

    /**
     * In dev mode, adds the test buyer and seller accounts, and gives the seeded admin
     * the test password and full name. Does nothing otherwise.
     * Called once, after the demo data is seeded.
     */
    public static void seedAccounts(DataStore data) {
        if (!isEnabled()) {
            return;
        }
        data.addUser(new Buyer(BUYER_USERNAME, "Buyer", PasswordHasher.hash(BUYER_PASSWORD), null));
        data.addUser(new Seller(SELLER_USERNAME, "Seller", PasswordHasher.hash(SELLER_PASSWORD)));

        User admin = data.findUser(ADMIN_USERNAME);
        admin.setFullName("Admin");
        admin.setPasswordHash(PasswordHasher.hash(ADMIN_PASSWORD));

        System.out.println("[DevLogin] Dev mode: test accounts ready, login forms will be pre-filled.");
    }

    /**
     * In dev mode, fills the login form of the portal for portalRole with that portal's
     * test account, and shows a note under the password field. Does nothing otherwise.
     */
    public static void prefill(UserRole portalRole, TextField usernameField, PasswordField passwordField) {
        if (!isEnabled()) {
            return;
        }
        switch (portalRole) {
            case BUYER -> fill(usernameField, passwordField, BUYER_USERNAME, BUYER_PASSWORD);
            case SELLER -> fill(usernameField, passwordField, SELLER_USERNAME, SELLER_PASSWORD);
            case ADMIN -> fill(usernameField, passwordField, ADMIN_USERNAME, ADMIN_PASSWORD);
        }

        Label note = new Label("DEV: test credentials pre-filled");
        note.setStyle("-fx-text-fill: #A68137; -fx-font-size: 11px;");
        if (passwordField.getParent() instanceof Pane parent) {
            parent.getChildren().add(note);
        }
    }

    private static void fill(TextField usernameField, PasswordField passwordField,
                             String username, String password) {
        usernameField.setText(username);
        passwordField.setText(password);
    }
}
