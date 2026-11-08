package com.hkust.marketplace.models;

/** A claim on a giveaway listing. It has no terms and cannot be countered (Claim in the domain class diagram). */
public class Claim extends Offer {

    public Claim(String listingId, String buyerUsername) {
        super(listingId, buyerUsername);
    }

    @Override
    public boolean canBeMadeOn(Listing listing) {
        return listing instanceof GiveawayListing;
    }

    @Override
    public String getKindName() {
        return "claim";
    }
}
