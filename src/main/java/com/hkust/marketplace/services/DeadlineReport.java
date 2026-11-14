package com.hkust.marketplace.services;

import com.hkust.marketplace.models.BorrowRequest;

import java.util.List;

/**
 * What one run of Process Borrowing Deadlines (UC12) did.
 *
 * @param reminded the borrow requests whose buyers were sent a due-date reminder (step 2)
 * @param returned the borrow requests whose items were returned automatically (step 3)
 */
public record DeadlineReport(List<BorrowRequest> reminded, List<BorrowRequest> returned) {

    /** A run that found no deadline reached, as most runs do. */
    public static final DeadlineReport NOTHING = new DeadlineReport(List.of(), List.of());

    public DeadlineReport {
        reminded = List.copyOf(reminded);
        returned = List.copyOf(returned);
    }

    /** True if the run sent a reminder or returned an item, so open screens should show the new data (step 4). */
    public boolean hasChanges() {
        return !reminded.isEmpty() || !returned.isEmpty();
    }
}
