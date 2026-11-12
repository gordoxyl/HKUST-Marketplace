# Shared core: guide for the Admin role (Task 3)

Audience: the AI agent (or developer) building the **Admin portal**. Everything below already exists,
is tested, and is meant to be **called, not rewritten**. Package root: `com.hkust.marketplace`.
Written 2026-11-10 by the shared-core owner (Gordon, Buyer role). If something here does not match the
code, the code and its Javadoc win; then tell Gordon.

Read first: `README.md` section "Shared Core", `docs/state-machines.md`, `docs/regression-log.md`.
The use-case spec `Diagrams and Use-Case Specifications.pdf` is authoritative for behaviour.

## 1. Hard rules (these break other roles if ignored)

1. **Layering (BCE).** Screen (controller / `ui`) → service (`services/`) → `data/DataStore` + `models/`.
   Put every rule, check and calculation in a service class with JUnit tests. Screens only read input,
   call a service, and show the result or the `ServiceException` message.
2. **Role check first.** Every admin-only service method starts with
   `Admin admin = AccessControl.requireAdmin();` (throws `AccessDeniedException`, a `ServiceException`).
   `DataStore` and `DealRules` do **not** check roles.
3. **Change model objects only inside `store.transaction(() -> { ... })`.** The store saves when the
   outermost transaction ends. A change made outside one is not saved (regression log R4, found in the
   admin dashboard).
4. **Time:** use `store.now()`, never `LocalDateTime.now()`. Records get their id and date from the store
   when added; never set them yourself.
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
10. **Already existing, do not duplicate:** `AccessControlTest`, `testsupport/Fixtures`, `TestClock`.

## 2. What is already done for the admin

| Admin use case (task) | Already provided | Still yours |
|---|---|---|
| Login (3.1) | `AuthService.login(username, password, UserRole.ADMIN)`; seeded `admin` / `Admin@1234` | nothing |
| Own profile (3.4) | `ProfileService.updateProfile`, shared `ui/ProfileView`, wired | nothing |
| Notification board (3.6) | `NotificationService`, shared `ui/NotificationBoard`, wired | send the right notifications |
| Listing Review (3.2) | `Listing.approve()/reject()`, `ListingActionRecord`, `ui/ListingCard` (admin view) | service; dashboard currently does it inline |
| Manage All Listings (3.7) | `Listing.remove()`, setters, `store.addListing`, `DealRules.declineOpenOffers`, `PhotoService` | service + edit/add forms (placeholders now) |
| Users (3.3) | `User.setActive`, **`store.deleteAccount`**, `AuditLogEntry`, `store.addAuditLogEntry` | service; "Delete" currently only deactivates |
| Seller Ratings (3.8) | `getReviewsForSellerIncludingHidden`, `Review.setHidden`, rating/stat queries | service + screen |
| All Deals Record (3.5) | `store.getAllOffers`, `store.getListingOf`, `ui/OfferText` | filters, export; table shows dummy rows now |

`controllers/admin/AdminDashboardController` implements `ContentHost` (`show(title, node)`), has
`showGreeting()`, and already uses the shared board, profile and listing card. The admin has no chat.

## 3. Models (`models/`)

- `User` → `Buyer`, `Seller`, `Admin`: `getUsername()` (lower case, immutable), `getFullName()`,
  `getRole()` (`UserRole.BUYER|SELLER|ADMIN`), `isActive()`, `setActive(boolean)`, `getCreatedAt()`.
  `Buyer.getResidenceHall()`; `Seller.getBio()`, `getPreferredMeetupLocations()`.
- `Listing` (abstract) → `SaleListing(seller, title, Category, ItemCondition, description, MeetupLocation, double price)`,
  `LendListing(..., Duration)`, `GiveawayListing(...)`. Setters for title, category, condition, description,
  meetupLocation, `SaleListing.setPrice`, `LendListing.setLendingDuration`, `setPhotos(List<String> ids)` (max 6).
  **State machine events** (throw `IllegalStateException` if not allowed):
  - `approve()`: Pending Review → Live. `reject()`: Pending Review → Rejected.
  - `remove()`: any status except Sold, Given, Removed → Removed; check with `canBeRemoved()`. The record
    stays in the store. A deal accepted before removal can still be completed; the listing stays Removed.
  - A listing the admin adds on behalf of a seller: `store.addListing(listing)` then `listing.approve()` (5.6.4).
    Check the typed username first: `store.findUser(typed) instanceof Seller` (A4, "Seller not found");
    `addListing` does not check it. Pass the username as typed: the listing keeps it in lower case
    without surrounding spaces (regression log R7).
- `ListingStatus`: PENDING_REVIEW, LIVE, REJECTED, RESERVED, SOLD, LENT, GIVEN, REMOVED.
- `ListingActionRecord(listingId, adminUsername, ListingAction, reason)`; `ListingAction`:
  EDIT_LISTING, ADD_LISTING, TAKEDOWN_LISTING, REMOVE_LISTING, APPROVE_LISTING, REJECT_LISTING;
  `action.requiresReason()`. Store with `store.addListingAction(record)`; read with
  `getListingActions(listingId)` / `getAllListingActions()`.
- `AuditLogEntry(adminUsername, username, actionTaken, reason, dataSnapshot)` (snapshot may be null);
  `store.addAuditLogEntry(entry)` gives `getLogReference()` and `getActionDate()`, and when the snapshot is
  not null also writes it as an encrypted file; `store.getAuditLog()`, `store.readAuditSnapshotFile(ref)`.
- `Offer` → `PriceOffer`, `BorrowRequest`, `Claim`: `getStatus()` (PENDING, ACCEPTED, DECLINED, COUNTERED),
  `getOfferDate()`, `getAcceptedDate()`, `getCompletedDate()`, `isOpen()`, `isCompleted()`, `isCurrent()`,
  `getBuyerUsername()`, `getListingId()`, `getKindName()`, `decline()`.
- `Review`: `getRating()`, `getComment()`, `getReviewDate()`, `getOfferId()`, `getSellerReply()`,
  `isFlagged()` (the seller flagged it for you), `isHidden()`, `setHidden(boolean)`.
- `Report`: `getListingId()`, `getBuyerUsername()`, `getReason()` (`ReportReason`), `getDetails()`.

## 4. `data/DataStore` (use `DataStore.getInstance()`; services take a `DataStore` in a second constructor for tests)

```java
store.transaction(() -> { ... });   store.read(() -> ...);   store.now();
// users
store.getAllUsers(); store.getUsersByRole(UserRole.SELLER); store.findUser(username); store.getFullName(username);
// listings
store.getAllListings(); store.getListingsByStatus(ListingStatus.PENDING_REVIEW); store.getListingsBySeller(u);
store.findListing(id); store.addListing(listing);
// deals
store.getAllOffers(); store.getOffersByBuyer(u); store.getOffersForSeller(u); store.getOffersForListing(id);
store.getListingOf(offer);
// ratings (Seller Ratings tab: average, total reviews, completed deals)
store.getAllReviews(); store.getReviewsForSellerIncludingHidden(seller);   // admin moderation ONLY
store.getReviewsForSeller(seller);            // what everyone else sees: hidden excluded
store.getAverageRating(seller); store.getRatingCount(seller);              // hidden excluded automatically
store.getCompletedDealCount(seller); store.getOffersReceivedCount(seller); store.getTotalViews(seller);
store.getSellerUsername(review); store.getBuyerUsername(review);
// reports
store.getAllReports(); store.getReportsForListing(listingId);
```

### `store.deleteAccount(username)` → `DeletedAccount` (Delete User, 6.4.4)
One atomic step that makes **all** the data changes of 6.4.4:
- **Deletes** the account, every chat thread the user took part in (UC6 SR2), the reviews the user wrote,
  the user's notifications.
- **Cancels:** declines the user's own open offers and the open offers on the user's listings; sets the
  user's listings to Removed (except Sold, Given, already Removed).
- **Keeps** listings and offers (accepted and completed deals too) as the archived transaction history.
  They still name the username, so it is **retired**: `addUser` / registration refuse it forever
  (`store.isRetiredUsername`). `store.getFullName(deleted)` returns the username.
- Deals accepted but not completed stay untouched (a borrowed item is still auto-returned; a reserved item
  can still be marked complete). Whether to forbid deleting a user with deals in progress is **your**
  use-case decision.
- Throws `IllegalArgumentException` if there is no such account. Does **not** check roles, validate the
  reason, write the Audit Log, or notify anyone: that is your service.

`DeletedAccount` record: `user()`, `chatThreads()`, `reviews()`, `notifications()`, `declinedOffers()`,
`removedListings()`, `snapshot()` (plain text of everything above as it was before, without the password
hash; use it as the Audit Log data snapshot, Special Requirement 4).

```java
public void deleteUser(String username, String reason) {
    Admin admin = AccessControl.requireAdmin();
    String why = TextInputs.required(reason).orElseThrow(() -> new ServiceException("A reason is required."));
    store.transaction(() -> {
        if (store.findUser(username) == null) throw new ServiceException("This account no longer exists.");
        DeletedAccount deleted = store.deleteAccount(username);
        store.addAuditLogEntry(new AuditLogEntry(admin.getUsername(), username, "Delete account", why, deleted.snapshot()));
        for (Offer o : deleted.declinedOffers()) { /* notify o.getBuyerUsername() and the listing's seller, except `username` */ }
    });
}
```
Decide in your service whether the admin may delete their own or the last admin account.
Notifying the deleted account itself is skipped silently by `NotificationService`.

## 5. Shared services

### `DealRules` (no role check)
- `declineOpenOffers(Listing, NotificationCategory, String because)`: declines every open offer on the
  listing and notifies each buyer "Your {kind} for '{title}' was declined because {because}." Returns the
  declined offers. Use it for:
  - **Remove listing (5.5.4)**: `rules.declineOpenOffers(listing, NotificationCategory.LISTING_REMOVED, "the admin removed the listing")`, then `listing.remove()`.
  - **Deactivate a seller (6.2.4)**: for each of the seller's listings, with `OFFER_UPDATE`.
- **Deactivate a buyer (6.2.4)** ("cancels all pending offers"): not provided; loop
  `store.getOffersByBuyer(u)`, `if (o.isOpen()) o.decline()`, and notify the affected sellers.
- A deactivated seller's listings disappear from Browse automatically (`store.isLive` / `isAvailable`
  check `seller.isActive()`); do not change their status. `user.setActive(true)` restores them.
- A deactivated account is refused at login (`LoginResult.DEACTIVATED`) and on its next service call.

### `NotificationService`
`send(username, NotificationCategory, text)`, `sendToAdmins(category, text)`. Categories you send:
`LISTING_APPROVED`, `LISTING_REJECTED` (include the reason), `LISTING_REMOVED`, `ACCOUNT_CHANGE`,
`ANNOUNCEMENT`, `OFFER_UPDATE`. Categories the admins receive: `LISTING_SUBMITTED`, `LISTING_REPORTED`,
`ACCOUNT_CHANGE`.

### `PhotoService`
`importPhoto(Path)` → photo id (PNG/JPEG/GIF/BMP; admin allowed), for editing or adding a listing
(5.4, 5.6). Then `listing.setPhotos(ids)`. Show with `new PhotoFrame(id, w, h)` (Task 3.2 "Photos").

### Hiding a review (6.6)
`store.transaction(() -> review.setHidden(true))`, then an `AuditLogEntry` (snapshot null) with the reason
and notifications to both parties (`store.getBuyerUsername(review)`, `store.getSellerUsername(review)`).
The average rating is recalculated automatically.

## 6. Shared UI kit (`ui/`)

- `Dialogs.confirm(title, msg)`, `info`, `error`, `problem(ServiceException)`,
  `askReason(title, question)` → `Optional<String>` (asks again until a reason is typed; empty = cancelled).
  `TextInputs.required(text)` → `Optional<String>` for the same check inside a service.
- `Formats`: `date`, `dateTime`, `period`, `price`, `duration`, `stars(int)`,
  `averageRating(OptionalDouble, count)`, `preview(text, max)`, `NONE`.
- `OfferText`: `summary(offer)`, `counter(offer)`, `status(offer)` ("Completed" once completed),
  `badgeStyle(offer)`, `kind(offer)`: for the All Deals table.
- `ListingCard`: `new ListingCard(listing, UserRole.ADMIN).addActions(approveBtn, rejectBtn)` shows seller
  name + username, submitted date-time, photo count and status; `ListingCard.grid()`.
- `PhotoFrame`, `NotificationBoard`, `ProfileView`, `ContentHost`.
- CSS classes in `css/styles.css`: `card`, `badge` + `badge-live|pending|sold|rejected`, `btn-primary`,
  `btn-outline`, `btn-danger`, `btn-success`, `btn-sm`, `section-title`.

## 7. Shape of an admin service (follow this)

```java
public class AdminListingService {
    private final DataStore store;
    private final NotificationService notifications;

    public AdminListingService() { this(DataStore.getInstance()); }
    public AdminListingService(DataStore store) { this.store = store; this.notifications = new NotificationService(store); }

    /** Manage Marketplace Listings 5.3: reject with a reason. */
    public void reject(String listingId, String reason) {
        Admin admin = AccessControl.requireAdmin();                                   // 1. role
        String why = TextInputs.required(reason)
                .orElseThrow(() -> new ServiceException("A reason is required."));    // 2. input
        store.transaction(() -> {                                                     // 3. atomic
            Listing listing = store.findListing(listingId);
            if (listing == null || listing.getStatus() != ListingStatus.PENDING_REVIEW) {
                throw new ServiceException("Only a listing that is pending review can be rejected.");
            }
            listing.reject();                                                         // 4. state machine
            store.addListingAction(new ListingActionRecord(listingId, admin.getUsername(),
                    ListingAction.REJECT_LISTING, why));
            notifications.send(listing.getSellerUsername(), NotificationCategory.LISTING_REJECTED,
                    "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + why);
        });
    }
}
```
Then replace the inline logic in `AdminDashboardController.createPendingCard` with calls to the service.

## 8. Testing support (`src/test/java/.../testsupport`)

```java
TestClock clock = Fixtures.clock();                 // starts at Fixtures.START; clock.advance(Duration)
DataStore store = Fixtures.store(clock);            // in memory, nothing written to disk
Admin ada = Fixtures.admin(store, "ada"); Seller sam = Fixtures.seller(store, "sam"); Buyer bea = Fixtures.buyer(store, "bea");
SaleListing pending = Fixtures.sale(store, "sam", 100);               // Pending Review
SaleListing live = Fixtures.live(Fixtures.sale(store, "sam", 100));   // also lend(...), giveaway(...)
Fixtures.moveTo(Fixtures.sale(store, "sam", 50), ListingStatus.SOLD); // any status, via the state machine
SessionManager.login(ada);                          // then call your service; SessionManager.logout() in @AfterEach
```
Examples to copy: `DealRulesTest`, `AccountDeletionTest`, `AccountDeletionFlowTest`, `PersistenceTest`
(`adminDeactivationAndReviewHiding_surviveARestart`, `auditEntryWithASnapshot_alsoWritesAnEncryptedTxtFile`).
Demo logins: admin `admin` / `Admin@1234`; sellers `charlie`, `diana`; buyers `alice`, `bob` (`Demo@1234`).

## 9. Known gaps on the admin side (as of 2026-11-10)

- Listing Review: approve/reject run inline in the controller, with raw `Alert`s instead of `Dialogs`.
- Manage All Listings: Edit and Add are placeholders; Remove does not decline open offers, record a
  `ListingActionRecord`, ask for a reason, or notify.
- Users: Deactivate does not decline offers, write the Audit Log or notify; Delete only deactivates.
- All Deals shows dummy rows; no export. Seller Ratings: hide has no reason, Audit Log entry or notifications.
