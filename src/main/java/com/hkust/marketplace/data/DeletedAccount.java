package com.hkust.marketplace.data;

import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.User;

import java.util.List;

/**
 * What {@link DataStore#deleteAccount} deleted and cancelled. The admin's Delete User keeps
 * the snapshot in the Audit Log and uses the lists to notify the users who are affected
 * (Manage All User Accounts and Reputation 6.4.4):
 * <pre>{@code
 * DeletedAccount deleted = store.deleteAccount(username);
 * store.addAuditLogEntry(new AuditLogEntry(admin.getUsername(), username, "Delete account",
 *         reason, deleted.snapshot()));
 * }</pre>
 * The reviews the user wrote are not in it: they are kept, and their writer is shown as
 * {@value DataStore#DELETED_USER} (client answer 6, 16 Nov 2026).
 *
 * @param user            the deleted account
 * @param chatThreads     the deleted chat threads the user took part in, with their messages
 * @param notifications   the deleted notifications from the user's board
 * @param declinedOffers  the offers that were still open and were declined: the user's own,
 *                        and those on the user's listings
 * @param removedListings the user's listings that became Removed
 * @param snapshot        all of the above as text, as it was just before the deletion, for
 *                        the Audit Log entry's data snapshot; the password hash is left out
 */
public record DeletedAccount(User user, List<ChatThread> chatThreads, List<Notification> notifications,
                             List<Offer> declinedOffers, List<Listing> removedListings, String snapshot) {

    public DeletedAccount {
        chatThreads = List.copyOf(chatThreads);
        notifications = List.copyOf(notifications);
        declinedOffers = List.copyOf(declinedOffers);
        removedListings = List.copyOf(removedListings);
    }
}
