package com.hkust.marketplace.services;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.NotificationCategory;
import com.hkust.marketplace.models.OfferStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UC12 Process Borrowing Deadlines (Task 1.6: auto-return; Task 1.8: due-date reminders):
 * as time passes, buyers are reminded that a borrowed item is nearly due, and items whose
 * borrowing period has ended are returned automatically.
 *
 * <p><b>Design (Lectures 15-16).</b> The actor is the Timer, not a person: the active class
 * {@link BorrowingDeadlineTimer} calls {@link #process()} at least once a second. This is the
 * use case's control class, so there is no logged-in user to check here. The entities are the
 * borrow requests and their listings. What a return does to the listing and whom it notifies
 * is the same as for a buyer's own return, so it is done by {@link DealRules#returnItem}.
 *
 * <p><b>Running on another thread.</b> {@link #process()} is called from the timer's thread
 * while the user works in the screens. All its changes are one {@link DataStore#transaction},
 * so a buyer who returns an item at the same moment either finds it borrowed or finds it
 * returned, never half of each (Special Requirement 5).
 */
public class BorrowingDeadlineService {

    private final DataStore store;
    private final DealRules rules;
    private final NotificationService notifications;

    public BorrowingDeadlineService() {
        this(DataStore.getInstance());
    }

    public BorrowingDeadlineService(DataStore store) {
        this.store = store;
        this.rules = new DealRules(store);
        this.notifications = new NotificationService(store);
    }

    /**
     * One run of the use case, for the moment the store's clock shows now:
     * <ol>
     *   <li>{Send Reminders}: every borrowed item that has reached its reminder time and has
     *       had no reminder yet gets one, with the item title and the due date (step 2).</li>
     *   <li>{Return Item}: every borrowed item whose due date has come is returned
     *       automatically. The due date becomes the completion date, the listing is Live
     *       again unless the admin removed it (A1), and buyer and seller are notified
     *       (step 3).</li>
     * </ol>
     * A borrowed item is an accepted borrow request that has not been returned. An item whose
     * reminder time and due date have both come, for example because the app was closed in
     * between, is reminded and then returned in the same run.
     * @return what was done; {@link DeadlineReport#NOTHING} if no deadline was reached
     */
    public DeadlineReport process() {
        // Most runs find nothing to do. Looking first, without a transaction, keeps them from saving.
        if (!store.read(this::hasWork)) {
            return DeadlineReport.NOTHING;
        }
        return store.transaction(() -> {
            LocalDateTime now = store.now();
            List<BorrowRequest> reminded = new ArrayList<>();
            for (BorrowRequest request : borrowedItems()) {
                if (isReminderDue(request, now)) {
                    remind(request);
                    reminded.add(request);
                }
            }
            List<BorrowRequest> returned = new ArrayList<>();
            for (BorrowRequest request : borrowedItems()) {
                if (isDue(request, now)) {
                    rules.returnItem(request, true);
                    returned.add(request);
                }
            }
            return new DeadlineReport(reminded, returned);
        });
    }

    private boolean hasWork() {
        LocalDateTime now = store.now();
        return borrowedItems().stream().anyMatch(request -> isReminderDue(request, now) || isDue(request, now));
    }

    /** The accepted borrow requests whose items have not been returned. */
    private List<BorrowRequest> borrowedItems() {
        return store.getAllOffers().stream()
                .filter(offer -> offer instanceof BorrowRequest
                        && offer.getStatus() == OfferStatus.ACCEPTED && !offer.isCompleted())
                .map(offer -> (BorrowRequest) offer)
                .toList();
    }

    /** Special Requirements 2 and 3: the reminder time has been reached, and no reminder was sent yet. */
    private static boolean isReminderDue(BorrowRequest request, LocalDateTime now) {
        return !request.isReminderSent() && !now.isBefore(request.getReminderTime());
    }

    /**
     * The borrowing period has ended. The due date itself counts, matching Manage My Deals,
     * where a buyer can return an item only before the due date (UC7 Special Requirement 3).
     */
    private static boolean isDue(BorrowRequest request, LocalDateTime now) {
        return !now.isBefore(request.getDueDate());
    }

    private void remind(BorrowRequest request) {
        Listing listing = store.getListingOf(request);
        notifications.send(request.getBuyerUsername(), NotificationCategory.DUE_DATE_REMINDER,
                "Reminder: '" + listing.getTitle() + "' is due on "
                        + BorrowRequest.formatDateTime(request.getDueDate()) + ".");
        request.markReminderSent();
    }
}
