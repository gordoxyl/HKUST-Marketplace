# Shared core: guide for the Seller role (Task 2)

Audience: the AI agent (or developer) building the **Seller portal**. Everything below already exists,
is tested, and is meant to be **called, not rewritten**. Package root: `com.hkust.marketplace`.
Written 2026-11-10 by the shared-core owner (Gordon, Buyer role). If something here does not match the
code, the code and its Javadoc win; then tell Gordon.

Read first: `README.md` section "Shared Core", `docs/state-machines.md`, `docs/regression-log.md`.
The use-case spec `Diagrams and Use-Case Specifications.pdf` is authoritative for behaviour.

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
6. **Tests:** run `.\mvnw.cmd clean test` (always `clean`). `.\mvnw.cmd clean verify` also enforces 65%
   branch coverage on non-screen code and builds JavaDoc. JavaFX screen classes are excluded from coverage
   via `jacoco.ui.excludes` in `pom.xml`; add a new pure-JavaFX class there, never a class with logic.
7. **Do not edit** `models/`, `data/`, shared `services/` (`DealRules`, `NotificationService`,
   `ChatService`, `PhotoService`, `ProfileService`, `AuthService`, `AccessControl`) or `ui/` without
   telling Gordon. If a model class or constructor changes, `data/JsonMapping` must change too.
8. **Bugs:** reproduce, add a test, fix, and add an entry to `docs/regression-log.md` (template at its end).
9. Never delete or commit `marketplace-data/`. Reset demo data with `scripts\reset-data.cmd`.

## 2. What is already done for the seller

| Seller use case (task) | Already provided | Still yours |
|---|---|---|
| Register as Seller (2.1), Login (2.2) | `AuthService.registerSeller`, `AuthService.login`; screens wired | nothing |
| Manage Profile (2.6) | `ProfileService.updateSellerProfile`, shared `ui/ProfileView`, wired | nothing |
| Notification Board (2.7) | `NotificationService`, shared `ui/NotificationBoard`, wired | send the right notifications from your services |
| Chat (2.5 "open the linked chat thread") | `ChatService`, `ui/ChatView`, `ui/ChatList`, "Chats" sidebar button wired | call `openForOffer` from your offers screen |
| Create Listing (2.3) | models, `store.addListing`, `PhotoService.importPhoto` | service + form (photo chooser is a placeholder; duration is whole days only) |
| My Listings (2.4) | `ui/ListingCard` already used; `Listing` state machine; `DealRules.declineOpenOffers` | edit/delete service (current buttons are placeholders / inline) |
| Manage Offers & Requests (2.5) | `DealRules.accept`, offer model methods, `ui/OfferText` | service (accept / decline / counter) + screen |
| Seller Stats (2.8) | `store.getTotalViews / getOffersReceivedCount / getCompletedDealCount / getAverageRating / getRatingCount` | screen still shows demo numbers; charts |
| Reviews (2.9) | `store.getReviewsForSeller`, `Review.reply`, `Review.flag` | service + screen |

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
    store.getAverageRating(username);          // OptionalDouble, hidden excluded
    store.getRatingCount(username);
    store.getTotalViews(username); store.getOffersReceivedCount(username); store.getCompletedDealCount(username);
    store.getBuyerUsername(review);            // who wrote a review
}
```
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
  `averageRating(OptionalDouble, count)` ("No ratings yet" when none), `preview(text, max)`, `NONE`.
- `OfferText`: `summary(offer)` ("Price offer: HK$ 160"), `counter(offer)`, `status(offer)`,
  `badgeStyle(offer)`, `kind(offer)`. Use it on the offers screen.
- `ListingCard`: `new ListingCard(listing, UserRole.SELLER).addActions(editBtn, deleteBtn)`; `ListingCard.grid()`.
- `PhotoFrame`, `ChatView`, `ChatList`, `NotificationBoard`, `ProfileView`, `ContentHost`.
- CSS classes in `css/styles.css`: `card`, `badge` + `badge-live|pending|sold|rejected`, `btn-primary`,
  `btn-outline`, `btn-danger`, `btn-success`, `btn-sm`, `section-title`.

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

## 9. Known gaps on the seller side (as of 2026-11-10)

- Create Listing: photo chooser not wired; lending duration is entered in whole days, but the 5-second
  borrow test (Task 1.5) needs shorter durations to be possible.
- My Listings: Edit is a placeholder; Delete calls `withdraw()` inline and does not decline open offers.
- No Offers & Requests screen yet; Stats shows demo numbers; Reviews has no reply/flag service.
