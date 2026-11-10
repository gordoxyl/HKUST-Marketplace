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

/** Tests for the wording of the shared chat screens (UC6 step 2 and A2). */
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
    void scrollPosition_putsTheLineInTheMiddle_andStaysBetweenTopAndBottom() {
        // A history 1000 high seen through a window 200 high: 800 can be scrolled
        assertEquals(0.5, ChatText.scrollPosition(480, 40, 1000, 200), 1e-9);  // line centred at 500
        assertEquals(0.0, ChatText.scrollPosition(0, 40, 1000, 200), 1e-9);    // first line: cannot go above the top
        assertEquals(1.0, ChatText.scrollPosition(960, 40, 1000, 200), 1e-9);  // last line: cannot go below the bottom
        assertEquals(0.0, ChatText.scrollPosition(80, 40, 1000, 200), 1e-9);   // centred exactly at the top edge
        assertEquals(0.125, ChatText.scrollPosition(180, 40, 1000, 200), 1e-9);
    }

    @Test
    void scrollPosition_isTheTop_whenTheWholeHistoryIsVisible() {
        assertEquals(0.0, ChatText.scrollPosition(50, 40, 200, 200), 1e-9);
        assertEquals(0.0, ChatText.scrollPosition(50, 40, 120, 200), 1e-9);
    }

    @Test
    void searchSummary_countsTheMatches() {
        assertEquals("No messages contain 'lamp'.", ChatText.searchSummary("lamp", 0)); // A2 step 2
        assertEquals("1 message contains 'lamp'.", ChatText.searchSummary(" lamp ", 1));
        assertEquals("3 messages contain 'lamp'.", ChatText.searchSummary("lamp", 3));
    }
}
