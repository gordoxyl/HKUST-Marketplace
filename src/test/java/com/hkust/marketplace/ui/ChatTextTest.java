package com.hkust.marketplace.ui;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.MeetupLocation;
import com.hkust.marketplace.models.PriceOffer;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.services.ChatEntry;
import com.hkust.marketplace.services.Conversation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests for the wording of the shared chat screens (UC6 step 2 and A2) and their deal actions (assumptions register 71). */
class ChatTextTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 9, 14, 5);

    private final SaleListing book = new SaleListing("sam", "Calculus Textbook", Category.TEXTBOOKS,
            ItemCondition.USED, "A book", MeetupLocation.LIBRARY, 180);
    private final ChatEntry fromBea = new ChatEntry(0, "bea", "Bea Chan", TIME, "Is it still available?", null);
    private final ChatEntry fromSam = new ChatEntry(1, "sam", "Sam Lee", TIME.plusMinutes(3), "Yes, it is.", null);
    private final ChatEntry beaOffer = new ChatEntry(2, "bea", "Bea Chan", TIME.plusMinutes(5), null,
            new PriceOffer("L1", "bea", 160));

    private Conversation seenBy(String viewer, ChatEntry... entries) {
        return new Conversation(book, "bea", "Bea Chan", "Sam Lee", viewer, List.of(entries));
    }

    @Test
    void titleAndSubtitle_nameTheListingAndTheOtherParty() {
        assertEquals("Chat: Calculus Textbook", ChatText.title(seenBy("bea")));
        assertEquals("Sell · HK$ 180 · with Sam Lee", ChatText.subtitle(seenBy("bea")));
        assertEquals("Sell · HK$ 180 · with Bea Chan", ChatText.subtitle(seenBy("sam")));
    }

    @Test
    void header_showsSenderAndTime_andCallsTheViewerYou() {
        Conversation beaView = seenBy("bea", fromBea, fromSam);

        assertEquals("You · 09 Nov 2026 14:05", ChatText.header(beaView, fromBea));
        assertEquals("Sam Lee · 09 Nov 2026 14:08", ChatText.header(beaView, fromSam));
        assertEquals("Bea Chan · 09 Nov 2026 14:05", ChatText.header(seenBy("sam", fromBea, fromSam), fromBea));
        assertEquals("You", ChatText.who(seenBy("SAM", fromSam), fromSam)); // usernames ignore case
    }

    @Test
    void body_isTheMessage_orWhatTheOfferProposed() {
        assertEquals("Is it still available?", ChatText.body(fromBea));
        assertEquals("Price offer: HK$ 160", ChatText.body(beaOffer));
    }

    @Test
    void preview_showsTheNewestMessageOrOffer_withWhoSentIt() {
        assertEquals("No messages yet", ChatText.preview(seenBy("bea")));
        assertEquals("Sam Lee: Yes, it is.", ChatText.preview(seenBy("bea", fromBea, fromSam)));
        assertEquals("You: Price offer: HK$ 160", ChatText.preview(seenBy("bea", fromBea, fromSam, beaOffer)));
        assertEquals("Bea Chan: Price offer: HK$ 160", ChatText.preview(seenBy("sam", fromBea, fromSam, beaOffer)));
    }

    @Test
    void preview_cutsALongMessage() {
        ChatEntry longMessage = new ChatEntry(0, "sam", "Sam Lee", TIME, "word ".repeat(40), null);

        String preview = ChatText.preview(seenBy("bea", longMessage));

        assertEquals("Sam Lee: ".length() + ChatText.PREVIEW_LENGTH, preview.length());
        assertEquals('…', preview.charAt(preview.length() - 1));
    }

    @Test
    void searchSummary_countsTheMatches() {
        assertEquals("No messages contain 'lamp'.", ChatText.searchSummary("lamp", 0)); // A2 step 2
        assertEquals("1 message contains 'lamp'.", ChatText.searchSummary(" lamp ", 1));
        assertEquals("3 messages contain 'lamp'.", ChatText.searchSummary("lamp", 3));
    }

    // ---------- Deal actions beside Send (assumptions register 71) ----------

    @Test
    void theDealButtons_sayWhatIsWaiting_andHowMany() {
        assertEquals("Agree to Counter (1)", ChatText.agreeButton(1));
        assertEquals("Agree to Counter (2)", ChatText.agreeButton(2));
        // the seller's is a link: offers are answered only on Offers & Requests (Task 2.5)
        assertEquals("Respond in Offers & Requests (1)", ChatText.respondButton(1));
        assertEquals("Respond in Offers & Requests (3)", ChatText.respondButton(3));
    }

    @Test
    void theAgreeDialog_isTitledAndHeadedWithTheSellerAndTheListing() {
        Conversation buyerView = seenBy("bea");

        assertEquals("Agree to Counter", ChatText.AGREE_TO_COUNTER);
        assertEquals("Agree", ChatText.AGREE);
        assertEquals("Agree to a counter from Sam Lee\n'Calculus Textbook'", ChatText.choiceHeading(buyerView, 1));
        assertEquals("Agree to a counter from Sam Lee\n'Calculus Textbook', newest first",
                ChatText.choiceHeading(buyerView, 2));
    }

    @Test
    void anOfferInTheAgreeDialog_showsTheBuyersTermsAndTheCounter() {
        PriceOffer countered = new PriceOffer("L1", "bea", 150);
        countered.markAdded("O1", TIME);
        countered.counter(170);
        PriceOffer pending = new PriceOffer("L1", "bea", 140);
        pending.markAdded("O2", TIME);

        assertEquals("HK$ 150 → HK$ 170 · 09 Nov 2026 14:05", ChatText.choice(countered));
        assertEquals("HK$ 140 · 09 Nov 2026 14:05", ChatText.choice(pending)); // no counter: the terms alone
    }
}
