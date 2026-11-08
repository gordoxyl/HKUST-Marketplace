package com.hkust.marketplace.data;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hkust.marketplace.models.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * How the model classes are turned into JSON and back, for {@link EncryptedJsonStorage}.
 *
 * <p>The models stay free of library annotations: everything Jackson needs is attached here as
 * "mix-ins". Jackson saves every field (not the getters, so derived values such as
 * {@code getPriceDisplay()} are not saved). To load an object it calls the model's own
 * constructor, so the constructor's checks run on loaded data too, and then fills in the
 * remaining fields. The {@code "kind"} property records which subclass an object is (for
 * example Buyer or Seller), because User, Listing and Offer are abstract.
 *
 * <p>If you add a field to a model, it is saved automatically. If you add a model class or
 * change a constructor's parameters, update the matching mix-in below.
 */
final class JsonMapping {

    private JsonMapping() {
        // Utility class
    }

    static ObjectMapper newMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)      // "2026-11-02T12:00:00"
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)  // "PT5S"
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)   // older files with removed fields still load
                .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .addMixIn(User.class, UserMixIn.class)
                .addMixIn(Buyer.class, BuyerMixIn.class)
                .addMixIn(Seller.class, SellerMixIn.class)
                .addMixIn(Admin.class, AdminMixIn.class)
                .addMixIn(Listing.class, ListingMixIn.class)
                .addMixIn(SaleListing.class, SaleListingMixIn.class)
                .addMixIn(LendListing.class, LendListingMixIn.class)
                .addMixIn(GiveawayListing.class, GiveawayListingMixIn.class)
                .addMixIn(Offer.class, OfferMixIn.class)
                .addMixIn(PriceOffer.class, PriceOfferMixIn.class)
                .addMixIn(BorrowRequest.class, BorrowRequestMixIn.class)
                .addMixIn(Claim.class, ClaimMixIn.class)
                .addMixIn(ChatThread.class, ChatThreadMixIn.class)
                .addMixIn(Message.class, MessageMixIn.class)
                .addMixIn(Review.class, ReviewMixIn.class)
                .addMixIn(Report.class, ReportMixIn.class)
                .addMixIn(Notification.class, NotificationMixIn.class)
                .addMixIn(ListingActionRecord.class, ListingActionRecordMixIn.class)
                .addMixIn(AuditLogEntry.class, AuditLogEntryMixIn.class)
                .build();
    }

    // ---------- Abstract classes: record the subclass ----------

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Buyer.class, name = "Buyer"),
            @JsonSubTypes.Type(value = Seller.class, name = "Seller"),
            @JsonSubTypes.Type(value = Admin.class, name = "Admin")})
    abstract static class UserMixIn { }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SaleListing.class, name = "SaleListing"),
            @JsonSubTypes.Type(value = LendListing.class, name = "LendListing"),
            @JsonSubTypes.Type(value = GiveawayListing.class, name = "GiveawayListing")})
    abstract static class ListingMixIn { }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PriceOffer.class, name = "PriceOffer"),
            @JsonSubTypes.Type(value = BorrowRequest.class, name = "BorrowRequest"),
            @JsonSubTypes.Type(value = Claim.class, name = "Claim")})
    abstract static class OfferMixIn { }

    // ---------- Concrete classes: which constructor to call when loading ----------

    abstract static class BuyerMixIn {
        @JsonCreator
        BuyerMixIn(@JsonProperty("username") String username, @JsonProperty("fullName") String fullName,
                   @JsonProperty("passwordHash") String passwordHash, @JsonProperty("residenceHall") String residenceHall) { }
    }

    abstract static class SellerMixIn {
        @JsonCreator
        SellerMixIn(@JsonProperty("username") String username, @JsonProperty("fullName") String fullName,
                    @JsonProperty("passwordHash") String passwordHash) { }
    }

    abstract static class AdminMixIn {
        @JsonCreator
        AdminMixIn(@JsonProperty("username") String username, @JsonProperty("fullName") String fullName,
                   @JsonProperty("passwordHash") String passwordHash) { }
    }

    abstract static class SaleListingMixIn {
        @JsonCreator
        SaleListingMixIn(@JsonProperty("sellerUsername") String sellerUsername, @JsonProperty("title") String title,
                         @JsonProperty("category") Category category, @JsonProperty("condition") ItemCondition condition,
                         @JsonProperty("description") String description,
                         @JsonProperty("meetupLocation") MeetupLocation meetupLocation,
                         @JsonProperty("price") double price) { }
    }

    abstract static class LendListingMixIn {
        @JsonCreator
        LendListingMixIn(@JsonProperty("sellerUsername") String sellerUsername, @JsonProperty("title") String title,
                         @JsonProperty("category") Category category, @JsonProperty("condition") ItemCondition condition,
                         @JsonProperty("description") String description,
                         @JsonProperty("meetupLocation") MeetupLocation meetupLocation,
                         @JsonProperty("lendingDuration") Duration lendingDuration) { }
    }

    abstract static class GiveawayListingMixIn {
        @JsonCreator
        GiveawayListingMixIn(@JsonProperty("sellerUsername") String sellerUsername, @JsonProperty("title") String title,
                             @JsonProperty("category") Category category,
                             @JsonProperty("condition") ItemCondition condition,
                             @JsonProperty("description") String description,
                             @JsonProperty("meetupLocation") MeetupLocation meetupLocation) { }
    }

    abstract static class PriceOfferMixIn {
        @JsonCreator
        PriceOfferMixIn(@JsonProperty("listingId") String listingId, @JsonProperty("buyerUsername") String buyerUsername,
                        @JsonProperty("offeredPrice") double offeredPrice) { }
    }

    abstract static class BorrowRequestMixIn {
        @JsonCreator
        BorrowRequestMixIn(@JsonProperty("listingId") String listingId, @JsonProperty("buyerUsername") String buyerUsername,
                           @JsonProperty("proposedStartDate") LocalDateTime proposedStartDate,
                           @JsonProperty("proposedEndDate") LocalDateTime proposedEndDate) { }
    }

    abstract static class ClaimMixIn {
        @JsonCreator
        ClaimMixIn(@JsonProperty("listingId") String listingId, @JsonProperty("buyerUsername") String buyerUsername) { }
    }

    abstract static class ChatThreadMixIn {
        @JsonCreator
        ChatThreadMixIn(@JsonProperty("buyerUsername") String buyerUsername, @JsonProperty("listingId") String listingId,
                        @JsonProperty("sellerUsername") String sellerUsername) { }
    }

    abstract static class MessageMixIn {
        @JsonCreator
        MessageMixIn(@JsonProperty("senderUsername") String senderUsername, @JsonProperty("content") String content,
                     @JsonProperty("sentAt") LocalDateTime sentAt) { }
    }

    abstract static class ReviewMixIn {
        @JsonCreator
        ReviewMixIn(@JsonProperty("offerId") String offerId, @JsonProperty("rating") int rating,
                    @JsonProperty("comment") String comment) { }
    }

    abstract static class ReportMixIn {
        @JsonCreator
        ReportMixIn(@JsonProperty("listingId") String listingId, @JsonProperty("buyerUsername") String buyerUsername,
                    @JsonProperty("reason") ReportReason reason, @JsonProperty("details") String details) { }
    }

    abstract static class NotificationMixIn {
        @JsonCreator
        NotificationMixIn(@JsonProperty("recipientUsername") String recipientUsername,
                          @JsonProperty("category") NotificationCategory category,
                          @JsonProperty("content") String content) { }
    }

    abstract static class ListingActionRecordMixIn {
        @JsonCreator
        ListingActionRecordMixIn(@JsonProperty("listingId") String listingId,
                                 @JsonProperty("adminUsername") String adminUsername,
                                 @JsonProperty("typeOfAction") ListingAction typeOfAction,
                                 @JsonProperty("reason") String reason) { }
    }

    abstract static class AuditLogEntryMixIn {
        @JsonCreator
        AuditLogEntryMixIn(@JsonProperty("adminUsername") String adminUsername, @JsonProperty("username") String username,
                           @JsonProperty("actionTaken") String actionTaken, @JsonProperty("reason") String reason,
                           @JsonProperty("dataSnapshot") String dataSnapshot) { }
    }
}
