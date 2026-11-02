package com.hkust.marketplace.models;

/** The marketplace administrator. Admins cannot register; one admin is seeded (Task 3). */
public class Admin extends User {

    public Admin(String username, String fullName, String passwordHash) {
        super(username, fullName, passwordHash);
    }

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }
}
