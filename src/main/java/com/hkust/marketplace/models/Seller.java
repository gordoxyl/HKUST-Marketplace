package com.hkust.marketplace.models;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A student or staff member using the seller portal (Task 2).
 *
 * The diagram's derived attributes (/averageRating, /ratingCount, /totalViews,
 * /offersReceived, /completedDeals) are not stored here. They are calculated from the
 * listings, offers and reviews in DataStore, so they can never go out of date.
 */
public class Seller extends User {

    private String bio;
    private List<MeetupLocation> preferredMeetupLocations = new ArrayList<>();

    public Seller(String username, String fullName, String passwordHash) {
        super(username, fullName, passwordHash);
    }

    @Override
    public UserRole getRole() {
        return UserRole.SELLER;
    }

    /** Null if the seller has no bio. */
    public String getBio() { return bio; }

    /** A blank bio is stored as no bio. */
    public void setBio(String bio) {
        this.bio = bio == null || bio.isBlank() ? null : bio.trim();
    }

    public List<MeetupLocation> getPreferredMeetupLocations() {
        return List.copyOf(preferredMeetupLocations);
    }

    /** Keeps the given order and drops duplicates. */
    public void setPreferredMeetupLocations(List<MeetupLocation> locations) {
        this.preferredMeetupLocations = locations == null
                ? new ArrayList<>()
                : new ArrayList<>(new LinkedHashSet<>(locations));
    }
}
