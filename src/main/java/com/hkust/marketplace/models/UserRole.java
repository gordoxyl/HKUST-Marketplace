package com.hkust.marketplace.models;

/**
 * The three kinds of account: buyer, seller and admin (UC1 Register, UC2 Log In).
 *
 * <p>Screens show a role with {@link #getDisplayName()}: "Buyer", not "BUYER". Unlike the other
 * model enums, {@code toString()} stays the constant's name, because the Audit Log's snapshot
 * of a deleted account records the role that way ({@code data/AccountSnapshot}).
 */
public enum UserRole {
    BUYER("Buyer"),
    SELLER("Seller"),
    ADMIN("Admin");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    /** The role as screens show it: "Buyer", "Seller" or "Admin". */
    public String getDisplayName() {
        return displayName;
    }
}
