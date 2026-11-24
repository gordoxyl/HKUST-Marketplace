package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.User;

import java.util.List;
import java.util.Locale;

/**
 * The wording of the notices about user accounts on the admins' Notification Board (Task 3.6, "user account
 * changes / updates"; our spec UC4 Special Requirement 2, "user account change"; client question F4, decided by
 * the team on 24 Nov 2026). Every change to a buyer's or seller's account is sent to every admin with
 * {@code NotificationService.sendToAdmins(NotificationCategory.ACCOUNT_CHANGE, ...)}, so the board reads as the
 * accounts' history:
 * <ul>
 *   <li>a new account (UC1 Register as Buyer, Register as Seller): {@link #created};</li>
 *   <li>a change a buyer or seller made to their own profile (UC3): {@link #profileChanged}, which names the
 *       fields and never their values, so no password or bio text reaches the board;</li>
 *   <li>an admin's edit, deactivation, reactivation or deletion of an account (Manage All User Accounts
 *       6.2-6.4): {@link #edited}, {@link #deactivated}, {@link #activated}, {@link #deleted}, with the admin
 *       and, where one is required, the reason. The Audit Log keeps the full record and the snapshots.</li>
 * </ul>
 * An admin's own profile is not a user account change. The wording is kept in this one class, so the shared core
 * and the admin portal word the history the same way.
 */
final class AccountNotices {

    /** The profile fields {@link #profileChanged} can name, in the order of the profile form (UC3). */
    static final String FULL_NAME = "full name";
    static final String PASSWORD = "password";
    static final String BIO = "bio";
    static final String MEETUP_LOCATIONS = "meet-up locations";

    private AccountNotices() {
        // Utility class
    }

    /** "New buyer account: Bea Chan (@bea)." */
    static String created(User user) {
        return "New " + role(user) + " account: " + name(user) + ".";
    }

    /**
     * "Bea Chan (@bea) changed their profile: full name, password."
     * @param fields the fields that changed, named with the constants above, in the order of the form; at least one
     */
    static String profileChanged(User user, List<String> fields) {
        return name(user) + " changed their profile: " + String.join(", ", fields) + ".";
    }

    /** "@ada edited the buyer account of Bea Chan (@bea)." */
    static String edited(User user, Admin admin) {
        return "@" + admin.getUsername() + " edited " + account(user) + ".";
    }

    /** "@ada deactivated the buyer account of Bea Chan (@bea). Reason: Spam" (the reason as the admin typed it). */
    static String deactivated(User user, Admin admin, String reason) {
        return "@" + admin.getUsername() + " deactivated " + account(user) + ". Reason: " + reason;
    }

    /** "@ada reactivated the buyer account of Bea Chan (@bea)." */
    static String activated(User user, Admin admin) {
        return "@" + admin.getUsername() + " reactivated " + account(user) + ".";
    }

    /**
     * "@ada deleted the buyer account of Bea Chan (@bea). Reason: Spam" (the reason as the admin typed it).
     * @param user the account as it was before the deletion, for its name and role
     */
    static String deleted(User user, Admin admin, String reason) {
        return "@" + admin.getUsername() + " deleted " + account(user) + ". Reason: " + reason;
    }

    private static String account(User user) {
        return "the " + role(user) + " account of " + name(user);
    }

    private static String name(User user) {
        return user.getFullName() + " (@" + user.getUsername() + ")";
    }

    private static String role(User user) {
        return user.getRole().name().toLowerCase(Locale.ROOT);
    }
}
