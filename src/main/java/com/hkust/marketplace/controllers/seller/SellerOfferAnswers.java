package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.BorrowRequest;
import com.hkust.marketplace.models.Offer;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.services.SellerOfferService;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.ChatView;
import com.hkust.marketplace.ui.TextInputs;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Task 2.5: the seller's answers to the offers in a chat (Manage Offers &amp; Requests 3.3-3.5).
 * The shared {@link ChatView} draws the Answer Offers / Answer Requests / Answer Claim button
 * and the dialog that picks an offer; this class is what the chosen action calls, the same way
 * the buyer's Make Offer and Agree to Counter are plugged in.
 *
 * <p><b>Two layers (Lecture 15).</b> Whether an action is allowed is decided by
 * {@link SellerOfferService}, which also checks the listing's state and the borrow period
 * (regression R8); this class only asks the seller, calls the service with the offer's id (the
 * object on screen may be out of date), and says what happened. The chat reloads after every
 * call, and opens the dialog again when a method returns false.
 *
 * <p><b>Drivers and stubs (Lecture 14).</b> What the seller is asked is behind
 * {@link SellerPrompts}, so {@code SellerOfferAnswersTest} can drive the three answers with a
 * stub instead of JavaFX dialogs. The form values are kept as typed ({@link SellerPrompts.Period}),
 * so a form shown again after a mistake still has them.
 */
final class SellerOfferAnswers implements ChatView.OfferAnswers {

    /** What the answers ask or tell the seller: {@link DialogPrompts} in the app, a stub in tests. */
    interface SellerPrompts {

        /** Asks the seller to confirm a destructive action; true only if they chose OK. */
        boolean confirm(String title, String message);

        /**
         * Asks for the counter price of a price offer.
         * @param typedBefore what the seller typed before, when the form comes back after a
         *                    mistake; null the first time, when the offer's price is suggested
         * @return the price as typed, or empty if the seller cancelled
         */
        Optional<String> askCounterPrice(PriceOffer offer, String typedBefore);

        /**
         * Asks for the new period of a borrow request.
         * @param before what the seller entered before, when the form comes back after a
         *               mistake; null the first time, when the request's period is suggested
         * @return the period as entered, or empty if the seller cancelled
         */
        Optional<Period> askCounterPeriod(BorrowRequest request, Period before);

        void info(String title, String message);

        void problem(ServiceException e);

        /** A counter period exactly as it stands in the form, so a form shown again has it. */
        record Period(LocalDate startDate, String startTime, LocalDate endDate, String endTime) {

            /** The start as a date and time, or null if the date is missing or the time is not a time of day. */
            LocalDateTime start() {
                return TextInputs.dateTime(startDate, startTime).orElse(null);
            }

            /** The end as a date and time, or null if the date is missing or the time is not a time of day. */
            LocalDateTime end() {
                return TextInputs.dateTime(endDate, endTime).orElse(null);
            }
        }
    }

    private final SellerOfferService offers;
    private final SellerPrompts prompts;

    SellerOfferAnswers(SellerOfferService offers, SellerPrompts prompts) {
        this.offers = offers;
        this.prompts = prompts;
    }

    /** 3.3: accept, after confirming; the service reserves or lends and declines the other offers. */
    @Override
    public boolean accept(Offer offer) {
        String becomes = offer instanceof BorrowRequest ? "Lent" : "Reserved";
        if (!prompts.confirm("Accept " + offer.getKindName(),
                "Accept this " + offer.getKindName() + "? The listing becomes " + becomes
                        + " and the other open offers on it are declined.")) {
            return false;
        }
        try {
            offers.accept(offer.getId());
        } catch (ServiceException e) {
            prompts.problem(e); // A2: the listing is no longer available, or the borrow period is over
            return false;
        }
        prompts.info("Offer accepted", "The buyer has been notified.");
        return true;
    }

    /** 3.4: decline one open offer, after confirming, and tell its buyer. */
    @Override
    public boolean decline(Offer offer) {
        if (!prompts.confirm("Decline " + offer.getKindName(),
                "Decline this " + offer.getKindName() + "? The buyer will be told.")) {
            return false;
        }
        try {
            offers.decline(offer.getId());
        } catch (ServiceException e) {
            prompts.problem(e);
            return false;
        }
        prompts.info("Offer declined", "The buyer has been notified.");
        return true;
    }

    /** 3.5: a price for a price offer, new dates for a borrow request; a claim has neither. */
    @Override
    public boolean counter(Offer offer) {
        if (offer instanceof PriceOffer price) {
            return counterPrice(price);
        }
        if (offer instanceof BorrowRequest request) {
            return counterBorrow(request);
        }
        return false; // a claim cannot be countered, and the dialog does not offer it
    }

    /** The form comes back with what was typed until the price is accepted or the seller cancels. */
    private boolean counterPrice(PriceOffer offer) {
        String typed = null;
        while (true) {
            Optional<String> answer = prompts.askCounterPrice(offer, typed);
            if (answer.isEmpty()) {
                return false;
            }
            typed = answer.get();
            try {
                offers.counterPrice(offer.getId(), typed);
            } catch (ServiceException e) {
                prompts.problem(e);
                continue;
            }
            prompts.info("Counter sent", "The buyer has been notified.");
            return true;
        }
    }

    /** The same, for the two dates and times of a borrow request. */
    private boolean counterBorrow(BorrowRequest request) {
        SellerPrompts.Period typed = null;
        while (true) {
            Optional<SellerPrompts.Period> answer = prompts.askCounterPeriod(request, typed);
            if (answer.isEmpty()) {
                return false;
            }
            typed = answer.get();
            try {
                offers.counterBorrow(request.getId(), typed.start(), typed.end());
            } catch (ServiceException e) {
                prompts.problem(e);
                continue;
            }
            prompts.info("Counter sent", "The buyer has been notified.");
            return true;
        }
    }
}
