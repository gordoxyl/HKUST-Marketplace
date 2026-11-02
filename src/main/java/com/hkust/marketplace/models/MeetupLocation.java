package com.hkust.marketplace.models;

/** Predefined HKUST meet-up locations (MeetupLocation in the domain class diagram). */
public enum MeetupLocation {
    ACADEMIC_BUILDING_LG1("Academic Building (LG1)"),
    LIBRARY("Library"),
    STUDENT_CENTRE("Student Centre"),
    HALL1_LOBBY("Hall 1 Lobby"),
    HALL2_LOBBY("Hall 2 Lobby"),
    HALL3_LOBBY("Hall 3 Lobby"),
    HALL4_LOBBY("Hall 4 Lobby"),
    HALL5_LOBBY("Hall 5 Lobby"),
    HALL6_LOBBY("Hall 6 Lobby"),
    HALL7_LOBBY("Hall 7 Lobby"),
    HALL8_LOBBY("Hall 8 Lobby"),
    HALL9_LOBBY("Hall 9 Lobby"),
    UG_HALL1("UG Hall 1"),
    UG_HALL2("UG Hall 2"),
    NORTH_GATE("North Gate"),
    SOUTH_GATE("South Gate"),
    SHAW_AUDITORIUM("Shaw Auditorium"),
    COFFEE_SHOP_LG5("Coffee Shop (LG5)"),
    SPORTS_HALL("Sports Hall");

    private final String displayName;

    MeetupLocation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The display name, so combo boxes and tables show readable text. */
    @Override
    public String toString() {
        return displayName;
    }
}
