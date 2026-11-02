package com.hkust.marketplace.models;

/** A listing for sale at a price (SaleListing in the domain class diagram). */
public class SaleListing extends Listing {

    private double price;

    public SaleListing(String sellerUsername, String title, Category category, ItemCondition condition,
                       String description, MeetupLocation meetupLocation, double price) {
        super(sellerUsername, title, category, condition, description, meetupLocation);
        setPrice(price);
    }

    @Override
    public ListingType getType() {
        return ListingType.SELL;
    }

    @Override
    public String getPriceDisplay() {
        return formatPrice(price);
    }

    public double getPrice() { return price; }

    /** @throws IllegalArgumentException if the price is not a positive number (Create Listings A2) */
    public void setPrice(double price) {
        if (!(price > 0) || Double.isInfinite(price)) {
            throw new IllegalArgumentException("The price must be a positive number.");
        }
        this.price = price;
    }

    /** "HK$ 180" for a whole amount, "HK$ 99.50" otherwise. */
    public static String formatPrice(double price) {
        return price == Math.rint(price)
                ? String.format("HK$ %.0f", price)
                : String.format("HK$ %.2f", price);
    }
}
