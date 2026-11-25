# Shared core: guide for the Admin role (Task 3)

Audience: the AI agent (or developer) building the **Admin portal**. Everything below already exists,
is tested, and is meant to be **called, not rewritten**. Package root: `com.hkust.marketplace`.
Written 2026-11-10 by the shared-core owner (Gordon, Buyer role); updated 2026-11-17 for the client's
answers, and 2026-11-22 for the buyers' reports (UC9). If something here does not match the code, the
code and its Javadoc win; then tell Gordon.

Read first: `README.md` section "Shared Core", `docs/state-machines.md`, `docs/regression-log.md`.
The use-case spec `Diagrams and Use-Case Specifications.pdf` is authoritative for behaviour, except where
the client (TA) answered differently: `docs/client-answers.md` wins over the spec.

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
6. **Tests:** run `.\mvnw.cmd clean test` (always `clean`). `.\mvnw.cmd clean verify` also builds JavaDoc and
   fails below 65% branch coverage, checked twice: on non-screen code (JavaFX screen classes are excluded via
   `jacoco.ui.excludes` in `pom.xml`; add a new pure-JavaFX class there, never a class with logic), and, since
   24 Nov, on the whole project, screens included (74.7% on 24 Nov; 73.5% with 4727541 merged). Untested
   screen or service code lowers the second figure: test new services, and a page's buttons with a screen test (§8).
7. **Do not edit** `models/`, `data/`, shared `services/` (`DealRules`, `NotificationService`,
   `ChatService`, `PhotoService`, `ProfileService`, `AuthService`, `AccessControl`) or `ui/` without
   telling Gordon. If a model class or constructor changes, `data/JsonMapping` must change too.
8. **Bugs:** reproduce, add a test, fix, and add an entry to `docs/regression-log.md` (template at its end).
9. Never delete or commit `marketplace-data/`. Reset demo data with `scripts\reset-data.cmd`.
10. **Already existing, do not duplicate:** `AccessControlTest`, `testsupport/Fixtures`, `TestClock`.

## 2. What is already done for the admin

| Admin use case (task) | Already provided (shared core) | Status on `main` (25 Nov 2026) |
|---|---|---|
| Login (3.1) | `AuthService.login(username, password, UserRole.ADMIN)`; seeded `admin` / `Admin@1234` | done |
| Own profile (3.4) | `ProfileService.updateProfile`, shared `ui/ProfileView`, wired | done |
| Notification board (3.6) | `NotificationService`, shared `ui/NotificationBoard`, wired; `services/AccountNotices` | done: the accounts' history. New accounts and users' own profile changes are sent by the shared core; the admin's own edit, deactivate, reactivate and delete by `AdminUserService` (PR #23) |
| Listing Review (3.2) | `Listing.approve()/reject()`, `ListingActionRecord`, `ui/ListingCard` (admin view) | done (`AdminListingService.approveListing` / `rejectListing`, reason through `Dialogs.askReason`) |
| Manage All Listings (3.7) | `Listing.remove()`, setters, `store.addListing`, `DealRules.declineOpenOffers`, `PhotoService` | done (`AdminListingService`: edit, add with photos, remove with a reason, open offers declined; photos in Add / Edit from PR #23) |
| Users (3.3) | `User.setActive`, **`store.deleteAccount`**, `AuditLogEntry`, `store.addAuditLogEntry` | done (`AdminUserService`: details, edit, deactivate, activate, delete with the Audit Log entry; the registration-date filter and the text search inside `findUsers` from PR #23) |
| Seller Ratings (3.8) | `getReviewsForSellerIncludingHidden`, `Review.setHidden`, rating/stat queries, `getReviewerName` (writer's name, "Deleted User" for a deleted buyer) | done (`AdminReputationService`: ratings, reviews with filters, hide with a reason) |
| All Deals Record (3.5) | `store.getAllOffers`, `store.getListingOf`, `ui/OfferText` | done (`AdminDealService`: filters, details, CSV export). Start Date (client answer #4): a loan's agreed borrow start (`BorrowRequest.getProposedStartDate()` of the accepted request), otherwise `getAcceptedDate()` |

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
// The calls an admin service makes on its DataStore ("store")
void dataStoreCalls(DataStore store, String username, String seller, String listingId,
                    Listing listing, Offer offer, Review review) {
    store.transaction(() -> { /* change model objects here */ });   // atomic write step, saved at the end
    store.read(() -> store.getAllUsers());                            // several reads as one step, never saves
    store.now();                                                      // the current time: never LocalDateTime.now()
    // users
    store.getAllUsers(); store.getUsersByRole(UserRole.SELLER); store.findUser(username); store.getFullName(username);
    // listings
    store.getAllListings(); store.getListingsByStatus(ListingStatus.PENDING_REVIEW); store.getListingsBySeller(seller);
    store.findListing(listingId); store.addListing(listing);
    // deals
    store.getAllOffers(); store.getOffersByBuyer(username); store.getOffersForSeller(seller); store.getOffersForListing(listingId);
    store.getListingOf(offer);
    // ratings (Seller Ratings tab: average, total reviews, completed deals)
    store.getAllReviews(); store.getReviewsForSellerIncludingHidden(seller);   // admin moderation ONLY
    store.getReviewsForSeller(seller);            // what everyone else sees: hidden excluded
    store.getRating(seller);                      // RatingSummary: count + average as one value, hidden excluded
    store.getAverageRating(seller); store.getRatingCount(seller);              // the same figures one by one
    store.getCompletedDealCount(seller); store.getOffersReceivedCount(seller); store.getTotalViews(seller);
    store.getSellerUsername(review); store.getBuyerUsername(review);
    store.getReviewerName(review);                // the writer's name on screens: "Deleted User" once deleted (answer #6)
    // reports, filed by buyers through Report Listing (UC9, services/ReportService)
    store.getAllReports(); store.getReportsForListing(listingId);
}
```

### `store.deleteAccount(username)` → `DeletedAccount` (Delete User, 6.4.4 with client answer #6)
One atomic step that makes **all** the data changes of 6.4.4, as the client's answer #6 (16 Nov,
`docs/client-answers.md`) changed them. **Done in the shared core on 20 Nov**:
- **Deletes** the account, every chat thread the user took part in (UC6 SR2), and the user's notifications.
- **Cancels:** declines the user's own open offers and the open offers on the user's listings; sets the
  user's listings to Removed (except Sold, Given, already Removed).
- **Keeps** listings and offers (accepted and completed deals too) as the archived transaction history, and
  the reviews about the user. Answer #6 also keeps **the reviews the user wrote** and **the reports filed by
  the user or on the user's listings**. A kept review is still shown and still counts in the seller's
  average and count; name its writer with `store.getReviewerName(review)`, which gives "Deleted User"
  (`DataStore.DELETED_USER`) once the buyer is deleted, and the full name otherwise.
  Everything kept still names the username, so it is **retired**: `addUser` / registration refuse it forever
  (`store.isRetiredUsername`). `store.getFullName(deleted)` returns the username.
- Deals accepted but not completed stay untouched (a borrowed item is still auto-returned; a reserved item
  can still be marked complete). Whether to forbid deleting a user with deals in progress is **your**
  use-case decision.
- Throws `IllegalArgumentException` if there is no such account. Does **not** check roles, validate the
  reason, write the Audit Log, or notify anyone: that is your service.
- Reports have no resolved or dismissed status (answer #7): the admin handles a report by acting on the
  listing, with Remove (`AdminListingService.removeListing`).

`DeletedAccount` record: `user()`, `chatThreads()`, `notifications()`, `declinedOffers()`,
`removedListings()`, `snapshot()` (plain text of what was deleted and cancelled, as it was before, without
the password hash; use it as the Audit Log data snapshot, Special Requirement 4). The kept reviews are not
in it, so the record has no `reviews()` any more.

```java
public void deleteUser(String username, String reason) {
    Admin admin = AccessControl.requireAdmin();
    String why = TextInputs.required(reason).orElseThrow(() -> new ServiceException("A reason is required."));
    store.transaction(() -> {
        if (store.findUser(username) == null) throw new ServiceException("This account no longer exists.");
        DeletedAccount deleted = store.deleteAccount(username);
        store.addAuditLogEntry(new AuditLogEntry(admin.getUsername(), username, "Delete account", why, deleted.snapshot()));
        for (Offer offer : deleted.declinedOffers()) {   // tell the other side of each cancelled offer
            Listing listing = store.getListingOf(offer);
            String other = offer.getBuyerUsername().equalsIgnoreCase(username)
                    ? listing.getSellerUsername() : offer.getBuyerUsername();
            notifications.send(other, NotificationCategory.OFFER_UPDATE, "The " + offer.getKindName() + " for '"
                    + listing.getTitle() + "' was cancelled because the account @" + username + " was deleted.");
        }
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

**The admins' board is the accounts' history (Task 3.6; client question F4, decided 24 Nov).** Every change to a
buyer's or seller's account goes to every admin, the admin who acted included, as
`sendToAdmins(NotificationCategory.ACCOUNT_CHANGE, ...)`. The wording comes from the package-private
`services/AccountNotices`, so every notice reads the same way:

| Event | Sent by | Wording (`AccountNotices`) |
|---|---|---|
| New account | `AuthService.registerBuyer` / `registerSeller` (shared core, built) | `created(user)`: "New buyer account: Bea Chan (@bea)." |
| The user's own profile change | `ProfileService.updateProfile` / `updateSellerProfile` (shared core, built) | `profileChanged(user, fields)`: "Bea Chan (@bea) changed their profile: full name, password." |
| Edit (Task 3.3) | your `AdminUserService.editUser` | `edited(user, admin)`: "@ada edited the buyer account of Bea Chan (@bea)." |
| Deactivate (6.2) | your `deactivateUser` | `deactivated(user, admin, cleanReason)`: "@ada deactivated the buyer account of Bea Chan (@bea). Reason: Spam" |
| Reactivate (6.3) | your `activateUser` | `activated(user, admin)`: "@ada reactivated the buyer account of Bea Chan (@bea)." |
| Delete (6.4) | your `deleteUser` | `deleted(user, admin, cleanReason)`: "@ada deleted the buyer account of Bea Chan (@bea). Reason: Spam" |

- Send it inside the action's own transaction, after the user's own notice (in `deleteUser`, after the audit
  entry; `user` was read before `store.deleteAccount`, so its name and role are still there).
- A profile notice names the fields that changed, never their values, so no password reaches the board. An
  admin's own profile (Task 3.4) sends nothing, and neither does a save that changes nothing.
- The user is not notified of their own change: the screen confirms it (UC3 step 9). An admin's change is
  notified to the user as before ("An administrator updated your account details.", 6.2.4, 6.3.1).

**Reports reach you from the buyer's Report Listing (UC9, built 2026-11-22).** Each report notifies every admin,
in category `LISTING_REPORTED`: "'<title>' by <seller's name> was reported (<reason>) and needs moderation."
- The notification names neither the reporter nor the details. Show both in VIEW (5.1) from
  `getReportsForListing`, as your `AdminListingService.getListingDetails` already does: only the admin may see
  who reported (UC9 SR4).
- A buyer reports any listing they can open: a live one, or one they have a deal on, whatever its status,
  so reports on Sold, Given or Removed listings are possible (assumption 80).
- "Reported" in the spec's Status filter is not a listing status. It is a permanent filter, "has at least
  one report", as your `ListingFilter.reportedOnly` already treats it (assumption 87).
- `store.addReport` now refuses a report on a listing that doesn't exist, or by an account that is not a
  buyer (`IllegalArgumentException`, regression log R10). Test data you add yourself must use a real
  buyer and listing, as `AdminListingQueryTest` already does.
- `ReportServiceTest.aReport_fromTheDetailScreen_toTheAdminsBoard_andTheAdminsViewOfTheListing` calls your
  `findListings(ListingFilter.none().withReportedOnly(true))` and `getListingDetails(id).reports()`. If
  you change either, change that test too (or tell Gordon).

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
- `Formats`: `date`, `dateTime` (to the minute), `borrowTime` and `period` (to the second: use them for borrow
  periods and due dates), `price`, `duration`, `stars(int)`,
  `averageRating(RatingSummary)` ("4.5 / 5 (2 ratings)" or "No ratings yet"; give it `store.getRating(seller)`), `datePickerConverter()` (date pickers show "21 Nov 2026"), `preview(text, max)`, `NONE`.
- `OfferText`: `summary(offer)`, `counter(offer)`, `status(offer)` ("Completed" once completed),
  `badgeStyle(offer)`, `kind(offer)`: for the All Deals table.
- `ListingCard`: `new ListingCard(listing, UserRole.ADMIN).addActions(approveBtn, rejectBtn)` shows seller
  name + username, submitted date-time, photo count and status; `ListingCard.grid()`.
- `PhotoFrame`, `NotificationBoard`, `ProfileView`, `ContentHost`.
- `ListingOverview`: the top of a listing's detail screen, the same as the buyer's: photos, title with badge and
  price, every field, description. `ListingOverview.card(node)` frames a part below it (§10).
- `NotificationDot`: the red number of unread notifications on the sidebar's Notifications button (§10).
- Buttons beside a field that grows (a message box, a search box) keep the width their text needs:
  `button.setMinWidth(Region.USE_PREF_SIZE)`. Otherwise a narrow row cuts their text to "…" (regression log R15).
  `PortalScreensTest` fails if any page cuts a button or label short at the app's window size.
- `UserRole.getDisplayName()`: "Buyer", "Seller", "Admin" for screens (its `toString()` stays "BUYER": the Audit
  Log's account snapshots record it that way).
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
// In a test class (see AccountDeletionTest for a whole one)
TestClock clock = Fixtures.clock();                 // starts at Fixtures.START; clock.advance(Duration)
DataStore store = Fixtures.store(clock);            // in memory, nothing written to disk
Admin ada = Fixtures.admin(store, "ada"); Seller sam = Fixtures.seller(store, "sam"); Buyer bea = Fixtures.buyer(store, "bea");
SaleListing pending = Fixtures.sale(store, "sam", 100);               // Pending Review
SaleListing live = Fixtures.live(Fixtures.sale(store, "sam", 100));   // also lend(...), giveaway(...)

@Test
void rejectingASoldListing_isRefused() {
    Listing sold = Fixtures.moveTo(Fixtures.sale(store, "sam", 50), ListingStatus.SOLD); // any status, via the state machine
    SessionManager.login(ada);                      // then call your service; SessionManager.logout() in @AfterEach
    assertThrows(ServiceException.class, () -> new AdminListingService(store).reject(sold.getId(), "Too late"));
}
```
Examples to copy: `DealRulesTest`, `AccountDeletionTest`, `AccountDeletionFlowTest`, `PersistenceTest`
(`adminDeactivationAndReviewHiding_surviveARestart`, `auditEntryWithASnapshot_alsoWritesAnEncryptedTxtFile`).
Demo logins: admin `admin` / `Admin@1234`; sellers `charlie`, `diana`; buyers `alice`, `bob` (`Demo@1234`).

**Screen tests (24 Nov 2026).** On every build, `controllers/PortalScreensTest.adminPortal_everyPageOpens` logs in as `admin` on the welcome screen and presses every sidebar button except Logout. It fails if a page throws while opening, opens any dialog, has a blank title, or has the same title as another page. Adding or renaming pages needs no change to it. To test a page's own buttons, add a class like `controllers/buyer/BuyerScreensTest` in `controllers/admin`:
```java
@Test
void editOpensTheListingForm() { // app = OffScreenApp.start() in @BeforeAll, app.reset() in @AfterEach
    app.logIn("Enter as Admin", "admin", SeedData.ADMIN_PASSWORD);
    app.press("All Listings");
    app.press("Edit", "IKEA Desk Lamp");    // the "Edit" button on the card that shows this text
    assertEquals(1, app.dialogs().size());  // every dialog is answered Cancel, or its only button
}
```
To fill in and submit a dialog instead, give its answer before the step: `app.answer("Edit listing", form -> form.type(...).press(...))`. The screens use the app's store, which in a test run is a temporary folder with the demo data, shared by all screen tests (storage manual §9.8). Do not depend on what another test changed; a test that changes data adds its own with `testsupport/ScreenData`. Examples to copy: `controllers/buyer/MakeOfferScreensTest`, `MyDealsScreensTest`.

## 9. Known gaps on the admin side (as of 2026-11-25, night)

Tasks 3.1 to 3.8 are all merged. Every admin screen calls its service: `AdminListingService` (PR #10), and
`AdminUserService`, `AdminDealService` and `AdminReputationService` (PR #17). PR #23 (29193ec) added photos in Add / Edit
listing, the registration-date filter and the text search on Manage Users, the four Task 3.6 notices to every admin,
`AdminReputationService.findReviewsForSeller`, their tests, the regression-log entries R12-R14 and the admin acceptance
scenarios. PR #24 (83e9b52) made the admin screens work like the buyer's and seller's: View listing, Add / Edit listing,
View and Edit user and the deal details open inside the app with "← Back to …", and a refused save keeps the form. The
date pickers use the shared converter, the filters say Search and Reset, deal types read Sell / Lend / Giveaway, the page
titles match the sidebar, the compiler warnings are gone, and the acceptance scenarios were corrected.

Follow-ups, none of them blocking the build:
- **Two regressions from PR #24, to fix first** (one line each; to log as R16 and R17):
  - editing a lend listing, the empty duration field's hint shows whole days ("e.g. 1" for a 36-hour loan) instead of
    "Leave empty to keep 1 day 12 hours", which invites R14's outcome;
  - on Edit and Add listing, the unit box shows "…" instead of "days": `unitBox.setPrefWidth(100)` is too narrow with
    the app's styles.
- **The shared UI** (§10): the unread dot; View listing through `ListingOverview` instead of its own copy of the photos,
  fields and description; roles through `UserRole.getDisplayName()` instead of the hand-made mapping.
- **Smaller:** Edit listing shows a Condition box that starts empty and is ignored (spec 5.4.2 doesn't make Condition
  editable); Remove on the listing's detail screen doesn't confirm first, as Remove on the card does; Back from a detail
  screen builds the list again, so its filters reset; Edit listing shows the price as "250.0" (`Amounts.plain` gives
  "250"); AT-UC4A-12 still says OK where the form says Save.
- **Optional:** parse the price in `AdminListingService` (the barricade) rather than in the screen; `createReviewRow`
  could get its data from the service; runs of 2+ blank lines remain in `AdminUserServiceTest`, `AdminListingServiceTest`,
  `AdminUserService` and `AdminTestSupport`.
- **Already right:** at the app's window size, no admin page cuts a button or label short (`PortalScreensTest`).

## 10. Added on 25 Nov 2026: the shared UI changes, and what the admin portal adds

Five UI changes were made in the shared core, the same in every portal (assumptions registers 89 to 93):
- **Nothing to do:** the Notification Board has **Mark all as read** (while anything is unread) and a **category
  filter**. The admins' board is the busiest (every account change, listing submission and report), so Mark all as
  read helps most here.
- **Three lines for the unread dot** (the red number on the sidebar's Notifications button):
  - at the end of `initialize()`: `NotificationDot.attach(btnNotifications, new NotificationService());`
  - at the end of `setActive(Button btn, String title)`: `NotificationDot.update();`
  - at the end of `show(String title, Node content)`: `NotificationDot.update();`

  The admin's sidebar pages fill `contentPane` themselves and never call `show(...)`, so the update in `setActive`
  is the one that runs on every page. It matters here: an admin's own account action notifies every admin, the one
  who acted included (F4), and Edit user → Save returns through `showUsers()`, which calls only `setActive`. Without
  that line the dot stays at 1 after the Save; with it, it shows 2.
- **View listing (spec 5.1)** already opens inside the app (PR #24). Replace its own copy of the photos, heading,
  fields and description with the shared `ListingOverview`, as the buyer's listing screen does:
  - `new ListingOverview(ListingSummary.of(listing, UserRole.ADMIN, store), store)` gives the photos, the title with
    the badge and price, every field and the description;
  - below it, "Reports (n)" with each report's reason and details in `ListingOverview.card(...)`, then Edit and Remove;
  - show it with `show(listing.getTitle(), new SubScreen("Back to All Listings", back, page))`, where `back` shows
    the same All Listings screen again, so its filters stay.
- **Roles:** show them with `UserRole.getDisplayName()` on the Manage Users cards and in the user details, instead of
  the hand-made `== UserRole.BUYER ? "Buyer" : "Seller"`.
- **Check:** log in as `admin` after a reset: the dot says 1; AT-UC4-08; View on a listing with photos opens inside
  the app, with the photos and Back to All Listings.
