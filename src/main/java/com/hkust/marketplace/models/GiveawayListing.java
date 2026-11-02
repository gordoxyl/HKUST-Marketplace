package com.hkust.marketplace.models;

/** A listing given away for free (GiveawayListing in the domain class diagram). */
public class GiveawayListing extends Listing {

    public GiveawayListing(String sellerUsername, String title, Category category, ItemCondition condition,
                           String description, MeetupLocation meetupLocation) {
        super(sellerUsername, title, category, condition, description, meetupLocation);
    }

    @Override
    public ListingType getType() {
        return ListingType.GIVEAWAY;
    }

    @Override
    public String getPriceDisplay() {
        return "Free";
    }
}
