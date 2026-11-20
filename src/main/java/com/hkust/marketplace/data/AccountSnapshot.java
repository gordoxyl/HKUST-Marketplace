package com.hkust.marketplace.data;

import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.ChatThread;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Message;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.Seller;
import com.hkust.marketplace.models.User;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Writes the text snapshot of an account that is about to be deleted, for the Audit Log
 * (Manage All User Accounts and Reputation Special Requirement 4: "a snapshot of the deleted
 * data is kept in the Audit Log"). The text is meant to be read by the admin, so it is plain
 * lines, one record per line, with dates in the form 2026-11-09T14:05.
 *
 * <p>The password hash is left out on purpose: it is of no use in an audit record.
 */
final class AccountSnapshot {

    private AccountSnapshot() {
        // Utility class
    }

    /**
     * Describes the account and everything {@link DataStore#deleteAccount} is about to delete or
     * cancel. The reviews the user wrote are not described: they are kept (client answer 6).
     */
    static String describe(DataStore store, User user, List<ChatThread> threads,
                           List<Notification> notifications, List<Offer> openOffers, List<Listing> listings) {
        StringBuilder text = new StringBuilder();
        describeAccount(text, user);
        describeThreads(text, store, threads);
        describeNotifications(text, notifications);
        describeOpenOffers(text, store, openOffers);
        describeListings(text, listings);
        return text.toString();
    }

    /** The account itself, without the password hash. */
    private static void describeAccount(StringBuilder text, User user) {
        text.append("DELETED ACCOUNT\n");
        text.append("Username: ").append(user.getUsername()).append('\n');
        text.append("Full name: ").append(user.getFullName()).append('\n');
        text.append("Role: ").append(user.getRole()).append('\n');
        text.append("Registered: ").append(date(user.getCreatedAt())).append('\n');
        text.append("Status: ").append(user.isActive() ? "Active" : "Deactivated").append('\n');
        if (user instanceof Buyer buyer) {
            text.append("Residence hall: ").append(orNone(buyer.getResidenceHall())).append('\n');
        }
        if (user instanceof Seller seller) {
            text.append("Bio: ").append(orNone(seller.getBio())).append('\n');
            text.append("Preferred meet-up locations: ").append(seller.getPreferredMeetupLocations()).append('\n');
        }
    }

    /** The chat threads, each with its messages. */
    private static void describeThreads(StringBuilder text, DataStore store, List<ChatThread> threads) {
        section(text, "CHAT THREADS DELETED", threads.size());
        for (ChatThread thread : threads) {
            text.append(thread.getId()).append(" | ").append(listing(store.findListing(thread.getListingId())))
                    .append(" | buyer @").append(thread.getBuyerUsername())
                    .append(" | seller @").append(thread.getSellerUsername())
                    .append(" | started ").append(date(thread.getCreatedAt())).append('\n');
            for (Message message : thread.getMessages()) {
                text.append("    ").append(date(message.getSentAt())).append(" @").append(message.getSenderUsername())
                        .append(": ").append(indented(message.getContent())).append('\n');
            }
        }
    }

    private static void describeNotifications(StringBuilder text, List<Notification> notifications) {
        section(text, "NOTIFICATIONS DELETED", notifications.size());
        for (Notification notification : notifications) {
            text.append(notification.getId()).append(" | ").append(date(notification.getTimestamp()))
                    .append(" | ").append(notification.getCategory().getDisplayName())
                    .append(" | ").append(notification.isRead() ? "read" : "unread")
                    .append(" | ").append(indented(notification.getContent())).append('\n');
        }
    }

    /** The open offers, with the status they had before they were declined. */
    private static void describeOpenOffers(StringBuilder text, DataStore store, List<Offer> openOffers) {
        section(text, "OPEN OFFERS DECLINED", openOffers.size());
        for (Offer offer : openOffers) {
            text.append(offer.getId()).append(" | ").append(offer.getKindName())
                    .append(" by @").append(offer.getBuyerUsername())
                    .append(" | ").append(listing(store.getListingOf(offer)))
                    .append(" | made ").append(date(offer.getOfferDate()))
                    .append(" | was ").append(offer.getStatus().getDisplayName()).append('\n');
        }
    }

    /** The listings, with the status they had before they were removed. */
    private static void describeListings(StringBuilder text, List<Listing> listings) {
        section(text, "LISTINGS REMOVED", listings.size());
        for (Listing listing : listings) {
            text.append(listing(listing)).append(" | was ").append(listing.getStatus().getDisplayName()).append('\n');
        }
    }

    private static void section(StringBuilder text, String title, int count) {
        text.append('\n').append(title).append(": ").append(count).append('\n');
    }

    /** "L12 'Desk Lamp' (seller @sam)". */
    private static String listing(Listing listing) {
        return listing.getId() + " '" + listing.getTitle() + "' (seller @" + listing.getSellerUsername() + ")";
    }

    private static String date(LocalDateTime time) {
        return time == null ? "unknown" : time.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String orNone(String value) {
        return value == null ? "none" : value;
    }

    /** Keeps a text of several lines inside its record: the following lines are indented. */
    private static String indented(String value) {
        return value.replace("\n", "\n        ");
    }
}
