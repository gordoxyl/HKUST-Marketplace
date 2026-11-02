package com.hkust.marketplace.models;

import java.util.List;

/** A student or staff member using the buyer portal (Task 1). */
public class Buyer extends User {

    /** Choices for the optional residence hall, used for meet-up suggestions (Task 1.1). */
    public static final List<String> RESIDENCE_OPTIONS = List.of(
            "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5",
            "Hall 6", "Hall 7", "Hall 8", "Hall 9",
            "UG Hall 1", "UG Hall 2", "Off-campus");

    /** Null if not given. Set only at registration (UC3 Special Requirement 1). */
    private final String residenceHall;

    public Buyer(String username, String fullName, String passwordHash, String residenceHall) {
        super(username, fullName, passwordHash);
        this.residenceHall = residenceHall == null || residenceHall.isBlank() ? null : residenceHall;
    }

    @Override
    public UserRole getRole() {
        return UserRole.BUYER;
    }

    public String getResidenceHall() { return residenceHall; }
}
