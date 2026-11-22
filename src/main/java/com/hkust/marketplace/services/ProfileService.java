package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;

import java.util.List;

/**
 * UC3 Manage Profile, shared by the User actor (Tasks 1.7, 2.6, 3.4): every user edits their
 * own full name and password; a seller also edits the bio and preferred meet-up locations.
 *
 * <p>The checks run in the order of the use case: full name (A1), new password (A2),
 * confirmation (A3). A problem is reported as a {@link ServiceException} with the message
 * to show, and then nothing is changed. The username and a buyer's residence hall cannot be
 * changed (Special Requirement 1), so there is no way to change them here.
 */
public class ProfileService {

    private final DataStore store;

    public ProfileService() {
        this(DataStore.getInstance());
    }

    public ProfileService(DataStore store) {
        this.store = store;
    }

    /** The logged-in user's profile, for showing the current values (UC3 step 2). */
    public User getMyProfile() {
        return AccessControl.requireLoggedIn();
    }

    /**
     * Saves the logged-in user's full name and, if one is typed, a new password (UC3 steps 5-9).
     * @param newPassword     empty or null keeps the current password (Special Requirement 2)
     * @param confirmPassword must match a typed new password; ignored otherwise
     * @throws ServiceException if a check fails (A1-A3); nothing is changed then
     */
    public void updateProfile(String fullName, String newPassword, String confirmPassword) {
        User user = AccessControl.requireLoggedIn();
        String cleanName = checkedName(fullName);
        String newHash = checkedNewPasswordHash(newPassword, confirmPassword);
        store.transaction(() -> {
            user.setFullName(cleanName);
            if (newHash != null) {
                user.setPasswordHash(newHash);
            }
        });
    }

    /**
     * Saves the logged-in seller's profile: full name, new password if one is typed, bio and
     * preferred meet-up locations (UC3 steps 3-9 for a seller).
     * @param bio       optional; blank means no bio
     * @param locations chosen from the predefined HKUST locations; may be empty
     * @throws AccessDeniedException if the logged-in user is not a seller
     * @throws ServiceException      if a check fails (A1-A3); nothing is changed then
     */
    public void updateSellerProfile(String fullName, String newPassword, String confirmPassword,
                                    String bio, List<MeetupLocation> locations) {
        Seller seller = AccessControl.requireSeller();
        String cleanName = checkedName(fullName);
        String newHash = checkedNewPasswordHash(newPassword, confirmPassword);
        store.transaction(() -> {
            seller.setFullName(cleanName);
            if (newHash != null) {
                seller.setPasswordHash(newHash);
            }
            seller.setBio(bio);
            seller.setPreferredMeetupLocations(locations);
        });
    }

    /** A1: the full name must not be empty. Any characters are allowed (Special Requirement 3). */
    private static String checkedName(String fullName) {
        String error = AccountValidator.validateFullName(fullName);
        if (error != null) {
            throw new ServiceException(error);
        }
        return fullName.trim();
    }

    /**
     * A2 and A3, only when a new password is typed. Returns the new password's hash, or null
     * to keep the current password.
     */
    private static String checkedNewPasswordHash(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            return null; // Special Requirement 2: blank keeps the current password
        }
        String error = AccountValidator.validatePasswordShowingRules(newPassword);
        if (error != null) {
            throw new ServiceException(error); // A2: say what is wrong and show the rules
        }
        error = AccountValidator.validateConfirmation(newPassword, confirmPassword);
        if (error != null) {
            throw new ServiceException(error); // A3
        }
        return PasswordHasher.hash(newPassword); // stored hashed only (Special Requirement 4)
    }
}
