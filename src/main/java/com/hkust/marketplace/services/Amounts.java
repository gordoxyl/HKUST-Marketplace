package com.hkust.marketplace.services;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Amounts of money as users type them. Typed text is untrusted input: services convert it
 * here, at the barricade (Lecture 10, defensive programming), and work with numbers after
 * that. One place, so every screen accepts and refuses the same texts.
 *
 * <p>This class was extracted when Make Offer (UC5) needed the same check as the price range
 * of Browse Marketplace (UC8): a refactoring that removed the duplicate.
 */
public final class Amounts {

    /** Digits, with an optional part of one or two decimals: "180" or "99.50". */
    private static final Pattern PLAIN_AMOUNT = Pattern.compile("[0-9]+(\\.[0-9]{1,2})?");

    private Amounts() {
        // Utility class
    }

    /**
     * The amount that was typed, in HK$, or empty if the text is not a plain amount: digits
     * with at most two decimals, such as "180" or "99.50". Spaces around it are ignored. A
     * sign, a currency symbol, a thousands separator or letters make it "not an amount", and
     * so does nothing at all. Zero is an amount; whether it is allowed is the caller's rule.
     */
    public static OptionalDouble parse(String typed) {
        if (typed == null || !PLAIN_AMOUNT.matcher(typed.strip()).matches()) {
            return OptionalDouble.empty();
        }
        double amount = Double.parseDouble(typed.strip());
        return Double.isInfinite(amount) ? OptionalDouble.empty() : OptionalDouble.of(amount);
    }

    /** The amount as it is typed into a box, without the currency: "180", or "99.50" when there are cents. */
    public static String plain(double amount) {
        return amount == Math.rint(amount)
                ? String.format(Locale.ROOT, "%.0f", amount)
                : String.format(Locale.ROOT, "%.2f", amount);
    }
}
