package com.hkust.marketplace.ui;

import javafx.scene.control.ComboBox;

/**
 * Filter choices of the form "All categories" followed by the values of an enum, as the buyer's
 * Browse Marketplace (UC8 step 3) and Deal History (UC11 step 3) screens use them. A screen
 * shows the values' display names, and asks for the chosen value back, or null for "all".
 * <pre>{@code
 * ComboBox<String> category = Choices.withAll("All categories", Category.values());
 * Category chosen = Choices.chosen(category, Category.values()); // null when "All categories" is chosen
 * }</pre>
 * Moved out of {@code BrowseView} when Deal History became its second user, so that both
 * screens offer their choices the same way (Lecture 10, refactoring: remove a duplicate at its
 * second use). The behaviour is unchanged.
 */
public final class Choices {

    private Choices() {
        // Utility class
    }

    /** A choice of "all", selected, followed by the values' display names ({@code toString()}). */
    public static ComboBox<String> withAll(String all, Enum<?>[] values) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().add(all);
        for (Enum<?> value : values) {
            box.getItems().add(value.toString());
        }
        box.getSelectionModel().selectFirst();
        return box;
    }

    /**
     * The value chosen in a box made by {@link #withAll}, or null when "all" is chosen.
     * @param values the same values, in the same order, as the box was made with
     */
    public static <E> E chosen(ComboBox<String> box, E[] values) {
        return valueAt(box.getSelectionModel().getSelectedIndex(), values);
    }

    /**
     * The value at a position of such a box: position 0 is "all" (null), position 1 the first
     * value. A box with nothing selected reports position -1, which also means "all".
     */
    static <E> E valueAt(int index, E[] values) {
        return index <= 0 ? null : values[index - 1];
    }
}
