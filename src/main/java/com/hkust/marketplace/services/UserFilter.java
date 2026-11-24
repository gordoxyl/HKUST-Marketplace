package com.hkust.marketplace.services;

import com.hkust.marketplace.models.UserRole;

import java.time.LocalDateTime;

/**
 * The filter options of the admin's user list (Task 3.3). A null field means "no filter on
 * this"; all chosen filters must match. Admin accounts are never listed, so the role filter
 * only distinguishes buyers from sellers.
 *
 * @param role     BUYER or SELLER, or null for both
 * @param active   TRUE for active accounts only, FALSE for deactivated accounts only, null for both
 * @param fromDate first registration moment, inclusive, or null
 * @param toDate   last registration moment, inclusive, or null
 */
public record UserFilter(UserRole role, Boolean active, LocalDateTime fromDate, LocalDateTime toDate) {

    /** No filter: every buyer and seller. */
    public static UserFilter none() {
        return new UserFilter(null, null, null, null);
    }

    public UserFilter withRole(UserRole value) {
        return new UserFilter(value, active, fromDate, toDate);
    }

    public UserFilter withActive(Boolean value) {
        return new UserFilter(role, value, fromDate, toDate);
    }

    public UserFilter withRegistrationRange(LocalDateTime from, LocalDateTime to) {
        return new UserFilter(role, active, from, to);
    }
}