# Shared core: guide for the Seller role (Task 2)

Audience: the AI agent (or developer) building the **Seller portal**. Everything below already exists,
is tested, and is meant to be **called, not rewritten**. Package root: `com.hkust.marketplace`.
Written 2026-11-10 by the shared-core owner (Gordon, Buyer role); updated 2026-11-17 for the client's
answers. If something here does not match the code, the code and its Javadoc win; then tell Gordon.

Read first: `README.md` section "Shared Core", `docs/state-machines.md`, `docs/regression-log.md`.
The use-case spec `Diagrams and Use-Case Specifications.pdf` is authoritative for behaviour, except where
the client (TA) answered differently: `docs/client-answers.md` wins over the spec.

## 1. Hard rules (these break other roles if ignored)

1. **Layering (BCE).** Screen (controller / `ui`) → service (`services/`) → `data/DataStore` + `models/`.
   Put every rule, check and calculation in a service class with JUnit tests. Screens only read input,
   call a service, and show the result or the `ServiceException` message.
2. **Role check first.** Every seller-only service method starts with
   `Seller seller = AccessControl.requireSeller();` (throws `AccessDeniedException`, a `ServiceException`).
   `DataStore` and `DealRules` do **not** check roles.
3. **Change model objects only inside `store.transaction(() -> { ... })`.** The store saves when the
   outermost transaction ends. A change made outside one is not saved (regression log R4).
4. **Time:** use `store.now()`, never `LocalDateTime.now()`. Records get their id and date from the store
   when added (`addListing`, `addOffer`, ...); never set them yourself.
5. **Errors:** throw `ServiceException("message written for the user")` for anything the user can cause.
   Models throw `IllegalArgumentException` / `IllegalStateException`; treat those as bugs in your service.
6. **Tests:** run `.\mvnw.cmd clean test` (always `clean`). `.\mvnw.cmd clean verify` also builds JavaDoc and
   fails below 65% branch coverage, checked twice: on non-screen code (JavaFX screen classes are excluded via
   `jacoco.ui.excludes` in `pom.xml`; add a new pure-JavaFX class there, never a class with logic), and, since
   24 Nov, on the whole project, screens included (74.7% on 24 Nov). Untested screen or service code lowers the
   second figure: test new services, and a page's buttons with a screen test (§8).
7. **Do not edit** `models/`, `data/`, shared `services/` (`DealRules`, `NotificationService`,
   `ChatService`, `PhotoService`, `ProfileService`, `AuthService`, `AccessControl`) or `ui/` without
   telling Gordon. If a model class or constructor changes, `data/JsonMapping` must change too.
8. **Bugs:** reproduce, add a test, fix, and add an entry to `docs/regression-log.md` (template at its end).
9. Never delete or commit `marketplace-data/`. Reset demo data with `scripts\reset-data.cmd`.

## 2. What is already done for the seller

| Seller use case (task) | Already provided (shared core) | Status on `main` (25 Nov 2026) |
|---|---|---|
| Register as Seller (2.1), Login (2.2) | `AuthService.registerSeller`, `AuthService.login`; screens wired | done |
| Manage Profile (2.6) | `ProfileService.updateSellerProfile`, shared `ui/ProfileView`, wired | done; page title "Manage Profile" |
| Notification Board (2.7) | `NotificationService`, shared `ui/NotificationBoard`, wired | done; page title "Notification Board"; the seller services send the notices |
| Chat (2.5 "open the linked chat thread") | `ChatService`, `ui/ChatView`, `ui/ChatList`, "Chats" sidebar button wired; under Send, the seller's link **Respond in Offers & Requests (n)** (`ChatView.setOnRespondToOffers`); `services/OfferAction` for the screen's buttons | done (52df814): every chat has the link, which opens Offers & Requests with the chat's offers marked; Open Chat on an offer has a Back button (see "From the chat to Offers & Requests") |
| Create Listing (2.3) | models, `store.addListing`, `PhotoService.importPhoto` | done (`CreateListingService`); follow-up in §9 |
| My Listings (2.4) | `ui/ListingCard` already used; `Listing` state machine; `DealRules.declineOpenOffers` | done (`SellerListingService`); client answer #5 (52df814): an edited Live listing with no open offers stays Live |
| Manage Offers & Requests (2.5) | `DealRules.accept`, offer model methods, `ui/OfferText` | done (`SellerOfferService`); buttons from `OfferAction`; the counter price is parsed with `Amounts.parse` in the service |
| Seller Stats (2.8) | `store.getTotalViews / getOffersReceivedCount / getCompletedDealCount / getAverageRating / getRatingCount` | done (52df814): `SellerStatsService` and `SellerStats`; a bar chart per listing, a pie chart by type, A1-A3 |
| Reviews (2.9) | `store.getReviewsForSeller`, `store.getReviewerName`, `Review.reply`, `Review.flag` | done (52df814): `SellerReviewService`; one reply, which the buyer gets as `REVIEW_REPLY`; one flag, which the admins get |

`controllers/seller/SellerDashboardController` implements `ContentHost` (`show(title, node)`), has
`showGreeting()`, and already uses the shared board, profile, chats and listing card.

## 3. Models (`models/`)

- `Seller extends User`: `getBio()`, `setBio(String)` (blank → null), `getPreferredMeetupLocations()`,
  `setPreferredMeetupLocations(List<MeetupLocation>)`. `User`: `getUsername()` (lower case, immutable),
  `getFullName()`, `isActive()`, `getRole()`, `getCreatedAt()`.
- `Listing` (abstract) → `SaleListing(seller, title, Category, ItemCondition, description, MeetupLocation, double price)`,
  `LendListing(..., Duration lendingDuration)`, `GiveawayListing(...)`. The type never changes after creation.
  - Getters/setters: title, category, condition, description, meetupLocation; `SaleListing.setPrice`,
    `LendListing.setLendingDuration`; `getPhotos()`, `setPhotos(List<String> photoIds)` (throws
    `IllegalArgumentException` above `Listing.MAX_PHOTOS` = 6), `getThumbnail()`; `getViewCount()`;
    `getPostedDate()`; `getStatus()`; `getType()`; `getPriceDisplay()`.
  - **State machine events** (each throws `IllegalStateException` if not allowed):
    `resubmit()` (seller saved an edit → Pending Review), `withdraw()` (seller deleted → Removed, the record
    stays), `acceptDeal()` / `completeDeal()` (called by `DealRules`, not by you).
    **Client answer of 16 Nov (`docs/client-answers.md` #5):** an edit of a **Live** listing that has **no
    open offers** keeps it Live, so do not call `resubmit()` then. Rejected and Pending Review listings are
    still resubmitted, and so is a Live listing whose open offers the edit declines (still to be confirmed
    with the client).
    `isLocked()` = Reserved, Sold, Lent, Given or Removed → cannot be edited or deleted (Manage Listings A2).
  - A new listing is `PENDING_REVIEW`; the admin approves it.
- `ListingStatus`: PENDING_REVIEW, LIVE, REJECTED, RESERVED, SOLD, LENT, GIVEN, REMOVED.
  `ListingType`: SELL, LEND, GIVEAWAY. `Category`, `ItemCondition`, `MeetupLocation` are enums whose
  `toString()` / `getDisplayName()` is the display text (usable directly in a `ComboBox`).
- `Offer` (abstract) → `PriceOffer` (`getOfferedPrice()`, `counter(double)`, `getCounterPrice()`),
  `BorrowRequest` (`getProposedStartDate/EndDate()`, `counter(start, end)`, `getCounterStartDate/EndDate()`,
  `getDueDate()`), `Claim` (no terms, cannot be countered).
  Common: `getId()`, `getListingId()`, `getBuyerUsername()`, `getStatus()` (PENDING, ACCEPTED, DECLINED,
  COUNTERED), `getOfferDate()`, `getAcceptedDate()`, `getCompletedDate()`, `isOpen()` (Pending or
  Countered), `isCompleted()`, `getKindName()` ("price offer" / "borrow request" / "claim"), `decline()`.
  Only a **Pending** offer can be accepted or countered; Pending or Countered can be declined.
- `Review`: `getRating()`, `getComment()`, `getReviewDate()`, `getOfferId()`, `getSellerReply()`,
  `reply(String)` (one reply only → `IllegalStateException`; empty → `IllegalArgumentException`),
  `isFlagged()`, `flag()`, `isHidden()`.

## 4. `data/DataStore` (use `DataStore.getInstance()`; services take a `DataStore` in a second constructor for tests)

```java
// The calls a seller service makes on its DataStore ("store")
void dataStoreCalls(DataStore store, String username, String listingId, String offerId,
                    Listing listing, Offer offer, Review review) {
    store.transaction(() -> { /* change model objects here */ });  // atomic write step, saved at the end; may return a value
    store.read(() -> store.findListing(listingId));                 // several reads as one consistent step, never saves
    store.now();                                                    // the current time: never LocalDateTime.now()
    store.addListing(listing);                 // gives id + posted date, status Pending Review
    store.findListing(listingId); store.getListingsBySeller(username);
    store.getOffersForSeller(username);        // every offer on the seller's listings
    store.getOffersForListing(listingId); store.findOffer(offerId); store.getListingOf(offer);
    store.getFullName(username);               // falls back to the username if the account was deleted
    store.getReviewsForSeller(username);       // hidden reviews already excluded: use this, nothing else
    store.getRating(username);                 // RatingSummary: count + average as one value, hidden excluded
    store.getAverageRating(username);          // OptionalDouble, hidden excluded
    store.getRatingCount(username);
    store.getTotalViews(username); store.getOffersReceivedCount(username); store.getCompletedDealCount(username);
    store.getBuyerUsername(review);            // who wrote a review
    store.getReviewerName(review);             // the writer's name to show: "Deleted User" once deleted
}
```
**Reviews by a deleted buyer (client answer #6, in the shared core since 20 Nov):** they stay in
`getReviewsForSeller` and still count in the rating and stats. Show a review's writer with
`store.getReviewerName(review)`, which gives "Deleted User" for them (not `"@" + getBuyerUsername`). A reply
notification to a deleted buyer needs no special case: `NotificationService.send` skips a deleted account.
For the stats charts (View Seller Stats steps 4-5): per-listing views = `listing.getViewCount()`, per-listing
offers = `store.getOffersForListing(id).size()`, completed deals by type = `getOffersForSeller` filtered by
`Offer::isCompleted`, grouped by `store.getListingOf(offer).getType()`.

## 5. Shared services

### `DealRules` (no role check: check ownership in your service, then call)
- `accept(Offer)`: offer → Accepted, listing → Reserved (sale, giveaway) or Lent (lend), every other open
  offer on the listing declined and its buyer notified, the accepted buyer notified. Throws
  `ServiceException` if the offer is not Pending, the listing is not Live (A2), or (since 2026-11-13,
  regression log R8) it is a borrow request whose period has already ended: show the message; the seller
  can still decline or counter it.
- **Borrowed items come back by themselves.** The app runs `BorrowingDeadlineTimer` (UC12): at the due date
  the item is returned automatically and the listing becomes Live again (stays Removed if the admin removed
  it), and the seller gets a Deal Completed notification. To show this on an open My Listings screen, make
  the screen implement `ui.Refreshable` and register the dashboard once in `initialize()`:
  `LiveUpdates.setOnDataChanged(() -> contentPane.getScene() != null, this::refreshCurrentScreen);`
  (see `BuyerDashboardController` for the 5-line pattern).
- `declineOpenOffers(Listing, NotificationCategory, String because)`: declines every open offer on the
  listing and notifies each buyer: "Your {kind} for '{title}' was declined because {because}." Returns the
  declined offers. **Call it when the seller saves an edit or deletes a listing** (Manage Listings A3),
  e.g. `rules.declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE, "the seller changed the listing")`.
- `complete(deal)` and `returnItem(request, automatic)` are the buyer's / timer's; you do not call them.
- **Not provided (write in your service):** declining one offer (`offer.decline()` + notify the buyer) and
  countering (`priceOffer.counter(price)` / `borrowRequest.counter(start, end)` + notify the buyer with
  `OFFER_UPDATE`). Do both inside one `store.transaction`.

### `NotificationService`
`send(username, NotificationCategory, text)`, `sendToAdmins(category, text)`. Categories you send:
`LISTING_SUBMITTED` (to admins, on create / resubmit), `OFFER_UPDATE` (to the buyer: accepted, declined,
countered), `REVIEW_REPLY` (to the buyer). Categories you receive: `LISTING_APPROVED`, `LISTING_REJECTED`,
`OFFER_RECEIVED`, `REVIEW_RECEIVED`, `DEAL_COMPLETED`, `NEW_MESSAGE`, `LISTING_REMOVED`, `ANNOUNCEMENT`.
Sending to an account the admin deleted returns null and is not an error.

### `ChatService` (UC6; buyer and seller)
```java
// "Open chat" on an incoming offer (Manage Offers & Requests 3.6); host is the dashboard (ContentHost)
void openChat(ChatService chatService, ContentHost host, Offer offer) {
    Conversation chat = chatService.openForOffer(offer.getId());
    host.show(ChatText.title(chat), new ChatView(chatService, chat));
}
```
Also `getMyConversations()` (the Chats screen, already wired), `send(chat, text)`, `search(chat, keyword)`,
`reload(chat)`. A seller cannot start a chat; one exists as soon as the buyer wrote or made an offer.

### From the chat to Offers & Requests (`ChatView.setOnRespondToOffers`; design of 24 Nov 2026)

The seller answers offers in one place: the Offers & Requests screen (Task 2.5; spec Manage Offers & Requests, steps 3.3–3.5). The chat links there instead of answering:
- Chat About Listing (UC6) has no answering step.
- Formal offers made from a thread go through Make Offer (UC6 Special Requirement 3).

This replaces the 19 Nov design (`ChatView.OfferAnswers`, never wired), which repeated the screen's Accept / Decline / Counter inside the chat. The link adds no data, rule or feature: it is navigation between two required screens, marked with the jump-and-highlight of the chat search (UC6 A2 step 4).

The chat's offer cards only show history and status. The deal actions are buttons in one row under Send, and each appears only while it is that person's turn:

| Listing type | Buyer (wired) | Seller (wired) |
|---|---|---|
| Sale | Make Offer; Agree to Counter (n) | Respond in Offers & Requests (n) |
| Lend | Request Borrow; Agree to Counter (n) | Respond in Offers & Requests (n) |
| Giveaway | Claim Item | Respond in Offers & Requests (1) |

`n` counts the chat's open offers, as `Conversation.offersToActOn()` lists them:
- for the buyer, the ones the seller countered;
- for the seller, the Pending and the Countered ones.

Both buyer actions are UC5 Make Offer: agreeing to a counter makes a new offer with the counter's terms (UC5 Special Requirement 4).

```java
// in ui/ChatView (shared core)
@FunctionalInterface
public interface RespondToOffers {
    /**
     * Pre: the viewer is this chat's seller; waiting is not empty and holds this chat's open offers
     * (Pending or Countered) of its one buyer on its one listing, newest first, exactly as
     * Conversation.offersToActOn() lists them.
     * Post: the chat itself changed nothing.
     */
    void respond(List<Offer> waiting);
}
public void setOnRespondToOffers(RespondToOffers respond) {
    // the seller's link to Offers & Requests, shown while the chat has open offers
}

// in ui/Scrolling (shared core; the chat search uses it too)
public static final String JUMP_HIGHLIGHT = "jump-highlight";   // the mark: light yellow, in styles.css
public static void bringIntoView(javafx.scene.control.ScrollPane pane, javafx.scene.Node node) {
    // lays the screen out, then centres the node as far as the content allows
}
```

- **What the chat guarantees:**
  - it shows the button only while the hook is set and the chat has open offers;
  - it calls `respond` on the JavaFX thread, with those offers, newest first;
  - it never changes the screen itself;
  - it never shows Accept, Decline or Counter to the seller.
- **What your hook does:**
  - It shows Offers & Requests with every offer in `waiting` marked with the `Scrolling.JUMP_HIGHLIGHT` style class (`jump-highlight`): the light yellow of a chat search result.
  - It scrolls the newest one into view with `Scrolling.bringIntoView(scrollPane, row)`.
  - The mark stays until the screen is built again: after an answer, or when the seller leaves. Not on a timer.
  - Offers answered in the meantime are no longer listed, so nothing is marked and no special case is needed.
  - A buyer can have two open offers on one listing (a Countered one and the offer made by agreeing to it), and both are marked.
- **Wiring (done, 52df814).** Every chat the seller opens gets the same hook, through `SellerDashboardController.addOfferLink`:
  - the Chats list: the 3-argument `ChatList(service, this, this::addOfferLink)`;
  - the chat that Open Chat (3.6) opens.

  A chat without the hook shows no seller button.
- **The same rules on the screen.**
  - `services/OfferAction.sellerButtons(type)` lists the seller's buttons for a listing type (no Counter for a giveaway).
  - `allowedFor(offer, false)` lists the ones an offer allows now (a Countered offer: Decline only).
  - Drive the buttons of Offers & Requests from them. `OfferActionTest` checks both against `SellerOfferService`.
- **Changes to the contract** go through Gordon (L11 change control).

### `PhotoService` (Create Listing step 8)
```java
// Create Listing step 8, when the listing is submitted
void addPhotos(PhotoService photoService, Listing listing, List<File> chosenFiles) {
    List<String> ids = new ArrayList<>();
    for (File f : chosenFiles) {
        ids.add(photoService.importPhoto(f.toPath()));   // PNG/JPEG/GIF/BMP, else ServiceException
    }
    listing.setPhotos(ids);                              // at most 6; first = thumbnail
}
```
Import **when the listing is submitted**, not when files are chosen. Enforce "at most 6" in your service
before importing (A4: reject the extra photos and tell the seller). Photos are stored encrypted in
`marketplace-data/marketplace-photos/`; a listing stores only ids. Show one with `new PhotoFrame(id, w, h)`.

### `ProfileService`, `AuthService`, `AccessControl`
Already wired. `AccountValidator` has the username / full name / password rules if you need them.

## 6. Shared UI kit (`ui/`)

- `Dialogs.confirm(title, msg)` (ask before every delete), `info`, `error`, `problem(ServiceException)`,
  `askReason(title, question)` → `Optional<String>`.
- `Formats`: `date`, `dateTime` (to the minute), `borrowTime` and `period(start, end)` (to the second: use them for
  borrow periods and due dates), `price(double)`, `duration(Duration)`, `stars(int)`,
  `averageRating(RatingSummary)` ("No ratings yet" when none; give it `store.getRating(username)`), `datePickerConverter()` (date pickers show "21 Nov 2026", like every other date), `preview(text, max)`, `NONE`.
- `OfferText`: `summary(offer)` ("Price offer: HK$ 160"), `counter(offer)`, `status(offer)`,
  `badgeStyle(offer)`, `kind(offer)`. Use it on the offers screen.
- `ListingCard`: `new ListingCard(listing, UserRole.SELLER).addActions(editBtn, deleteBtn)`; `ListingCard.grid()`.
- `PhotoFrame`, `ChatView`, `ChatList`, `NotificationBoard`, `ProfileView`, `ContentHost`.
- `ChatList` is the chat screen: the user's chats beside the open one, a toggle to hide the list, and Back above the
  chat. A chat opened from another screen: `new ChatList(service, this, this::addOfferLink).openFrom(chat, "Back to …", back)`.
- `NotificationDot`: the red number of unread notifications on the sidebar's Notifications button (§10).
- Buttons beside a field that grows (a message box, a search box) keep the width their text needs:
  `button.setMinWidth(Region.USE_PREF_SIZE)`. Otherwise a narrow row cuts their text to "…" (regression log R15).
  `PortalScreensTest` fails if any page cuts a button or label short at the app's window size.
- `Scrolling.bringIntoView(scrollPane, node)`: scrolls a node into the middle of the visible part (the chat search, and
  Offers & Requests for a chat's offers).
- CSS classes in `css/styles.css`: `card`, `badge` + `badge-live|pending|sold|rejected`, `btn-primary`,
  `btn-outline`, `btn-danger`, `btn-success`, `btn-sm`, `section-title`, and `jump-highlight` (the item a search
  result or a link jumped to).

## 7. Shape of a seller service (follow this)

```java
public class SellerListingService {
    private final DataStore store;
    private final DealRules rules;     // declineOpenOffers below also notifies the buyers

    public SellerListingService() { this(DataStore.getInstance()); }
    public SellerListingService(DataStore store) {
        this.store = store;
        this.rules = new DealRules(store);
    }

    /** Manage Listings 3.2: delete. */
    public void deleteListing(String listingId) {
        Seller seller = AccessControl.requireSeller();                       // 1. role
        store.transaction(() -> {                                            // 2. atomic
            Listing listing = store.findListing(listingId);
            if (listing == null || !listing.getSellerUsername().equals(seller.getUsername())) {
                throw new ServiceException("This listing is not yours.");    // 3. validate
            }
            if (listing.isLocked()) {
                throw new ServiceException("This listing can no longer be edited or deleted.");  // A2
            }
            rules.declineOpenOffers(listing, NotificationCategory.OFFER_UPDATE, "the seller deleted the listing");
            listing.withdraw();                                              // 4. state machine
        });
    }
}
```

## 8. Testing support (`src/test/java/.../testsupport`)

```java
// In a test class (see DealRulesTest for a whole one)
TestClock clock = Fixtures.clock();                 // starts at Fixtures.START; clock.advance(Duration)
DataStore store = Fixtures.store(clock);            // in memory, nothing written to disk
Seller sam = Fixtures.seller(store, "sam");  Buyer bea = Fixtures.buyer(store, "bea");
SaleListing book = Fixtures.live(Fixtures.sale(store, "sam", 100));   // also lend(...), giveaway(...)

@Test
void deletingAReservedListing_isRefused() {
    Listing reserved = Fixtures.moveTo(Fixtures.sale(store, "sam", 50), ListingStatus.RESERVED); // any status, via the state machine
    SessionManager.login(sam);                      // then call your service; SessionManager.logout() in @AfterEach
    assertThrows(ServiceException.class, () -> new SellerListingService(store).deleteListing(reserved.getId()));
}
```
Examples to copy: `DealRulesTest`, `ChatServiceTest`, `ProfileServiceTest`. Demo logins: sellers
`charlie`, `diana`, password `Demo@1234`.

**Screen tests (24 Nov 2026).** On every build, `controllers/PortalScreensTest.sellerPortal_everyPageOpens` logs in as `charlie` on the welcome screen and presses every sidebar button except Logout. It fails if a page throws while opening, opens any dialog, has a blank title, or has the same title as another page. Adding or renaming pages needs no change to it. To test a page's own buttons, add a class like `controllers/buyer/BuyerScreensTest` in `controllers/seller`:
```java
@Test
void declineAsksFirst() { // app = OffScreenApp.start() in @BeforeAll, app.reset() in @AfterEach
    app.logIn("Enter as Seller", "diana", SeedData.DEMO_PASSWORD);
    app.press("Offers & Requests");
    app.press("Decline", "Graphing Calculator TI-84"); // the "Decline" button on the card that shows this text (Bob's borrow request)
    assertEquals(1, app.dialogs().size());             // the confirmation, answered Cancel: every dialog gets Cancel, or its only button
}
```
To fill in and submit a dialog instead, give its answer before the step: `app.answer("Decline borrow request", form -> form.press("OK"))`. The screens use the app's store, which in a test run is a temporary folder with the demo data, shared by all screen tests (storage manual §9.8). Do not depend on what another test changed; a test that changes data adds its own with `testsupport/ScreenData`. Examples to copy: `controllers/buyer/MakeOfferScreensTest`, `MyDealsScreensTest`.

## 9. Known gaps on the seller side (as of 2026-11-25, night)

Tasks 2.1 to 2.9 are all merged:
- PR #11: `CreateListingService`, `SellerListingService`, `SellerOfferService`.
- PR #21 (52df814): the chat's link to Offers & Requests; Seller Stats (`SellerStatsService`); Reviews & Feedback (`SellerReviewService`); client answer #5. The buyer's "seller replied" notification (Task 1.8) now arrives.

Follow-ups, none of them blocking:
- **Unused imports** that IntelliJ's commit check reports: `SellerOfferServiceTest` (`Category`, `ItemCondition`, `MeetupLocation`) and `SellerStatsServiceTest` (`Offer`).
- **One `counterPrice` entry point.** The screen calls `counterPrice(String offerId, String typedPrice)`. The `double` overload is left only for `SellerOfferServiceTest` and `OfferActionTest`: let them pass text, and delete it, so every caller goes through `Amounts.parse`.
- **The listing price takes any number Java reads (to log as R18).** `SellerDashboardController.parsePrice` uses `Double.parseDouble`: on Create and Edit Listing, "99.999" is saved (the cards then show "HK$ 100.00") and "1e3" becomes HK$ 1000, which the counter price and the admin's form refuse. Read it with `Amounts.parse`, as the counter price is (in the service would be the full barricade). Edit Listing also shows the price as "250.0"; `Amounts.plain` gives "250".
- **The counter-borrow date pickers.** They show dates in the computer's locale, unlike every other screen ("26 Nov 2026"): give them `Formats.datePickerConverter()`. They also read only `getValue()`, so a date typed without Enter can be lost; `controllers/buyer/OfferDialogs.shownDate` reads the editor's text.
- **Screen reads of `DataStore`** (BCE, optional): buyer names on offer rows, and each review's writer and listing title, are read in `SellerDashboardController`. Return them from the services instead.
- **The submission:** run the seller acceptance scenarios (Create Listing to Manage Reviews, AT-CHAT-06 to 11 and AT-CHAT-19) on `main`; they are the script for the seller screenshots.
- **Wording:** the Create Listing error "Choose Sell, Lend or Give Away first." says "Give Away" where the type buttons
  and the cards say "Giveaway" (so do AT-2.3-03, AT-2.3-04 and AT-2.8-04); Reviews & Feedback draws its stars by hand
  instead of with `Formats.stars` (same look); the photo texts say "up to 6" instead of using `Listing.MAX_PHOTOS`.
- **The shared UI's wiring** in §10: the unread dot, and Open Chat on an offer through `openFrom`.
- **Already right:** at the app's window size, no seller page cuts a button or label short (`PortalScreensTest`).

## 10. Added on 25 Nov 2026: the shared UI changes, and what the seller portal adds

Five UI changes were made in the shared core, the same in every portal (assumptions registers 89 to 93). Most need
nothing from you:
- **Nothing to do:**
  - the Notification Board has **Mark all as read** (while anything is unread) and a **category filter**;
  - the Chats page is now the **chat screen**: your chats in a list beside the open chat, a button that hides and
    shows the list, and **Back to Chats** above a chat chosen there. Your
    `new ChatList(new ChatService(), this, this::addOfferLink)` stays as it is: `addOfferLink` runs on every chat
    the screen opens.
- **Three lines for the unread dot** (the red number on the sidebar's Notifications button):
  - at the end of `initialize()`: `NotificationDot.attach(btnNotifications, new NotificationService());`
  - at the end of `setActive(Button btn, String title)`: `NotificationDot.update();`
  - at the end of `show(String title, Node content)`: `NotificationDot.update();`

  My Listings, Create Listing, Offers & Requests, Seller Stats and Reviews & Feedback fill `contentPane` themselves
  and never call `show(...)`, so the update in `setActive` is the one that runs on every page. The board updates the
  dot after marking, and `LiveUpdates` after the timer's changes; nothing else to call.
- **Offers & Requests → Open Chat** (`openOfferChat`), so that chat opens in the same chat screen, with the list
  beside it and Back to Offers & Requests above. After `ChatService service = new ChatService();`, replace the
  lines that build the `ChatView`, add the link and show it in a `SubScreen` with:
  - `Conversation chat = service.openForOffer(offer.getId());`
  - `new ChatList(service, this, this::addOfferLink).openFrom(chat, "Back to Offers & Requests", this::showOffers);`

  Then remove the imports this leaves unused.
- **Check:**
  - log in as `charlie` after a reset: the dot says 2; on the board, Mark all as read removes it;
  - AT-CHAT-19, and Open Chat on an offer: the list is beside the chat, and Back returns to Offers & Requests.
