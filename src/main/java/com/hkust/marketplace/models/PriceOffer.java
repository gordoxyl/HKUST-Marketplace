package com.hkust.marketplace.models;

/** A price offer on a sale listing (PriceOffer in the domain class diagram). */
public class PriceOffer extends Offer {

    private final double offeredPrice;
    private Double counterPrice;

    /** @throws IllegalArgumentException if the price is not a positive number (UC5 A2) */
    public PriceOffer(String listingId, String buyerUsername, double offeredPrice) {
        super(listingId, buyerUsername);
        this.offeredPrice = requirePositive(offeredPrice);
    }

    @Override
    public boolean canBeMadeOn(Listing listing) {
        return listing instanceof SaleListing;
    }

    @Override
    public String getKindName() {
        return "price offer";
    }

    public double getOfferedPrice() { return offeredPrice; }

    /** Null unless the seller countered. */
    public Double getCounterPrice() { return counterPrice; }

    /** The seller counters with another price (Manage Offers &amp; Requests step 3.5). */
    public void counter(double price) {
        requirePositive(price);
        markCountered();
        counterPrice = price;
    }

    private static double requirePositive(double price) {
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new IllegalArgumentException("The price must be a positive amount.");
        }
        return price;
    }
}
