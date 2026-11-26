# Regression log

COMP 3111 Lecture 14 (regression testing): whenever we find a bug, we

1. **reproduce it** before fixing it,
2. **record the input** that produced it and the **correct output**,
3. **put both into the test suite**,
4. fix the bug and check that the test now passes.

The test keeps the bug from coming back. Every entry below names its test, and its **area / owner**: *shared core* (models, data, shared services; built by Gordon for the whole team), *Buyer* (Task 1), *Seller* (Task 2), *Admin* (Task 3) or *tooling*. We work out the area before fixing anything, so nobody changes another person's portal without telling them. Add new bugs at the end, using the same headings.

---

## R1: Demo deals dated "now" although accepted days earlier

- **Area / owner:** shared core (`data/SeedData`, model dates).
- **Found:** 2026-11-02, while reviewing the shared core. **Fixed:** 2026-11-08 (store clock; commit `4416c1a`).
- **Input:** start the app with the demo data (`SeedData.populate`).
- **Correct output:** for every offer, *listing posted ≤ offer date < accepted date < completed date < review date*.
- **Output before the fix:** every offer's date was the moment the app started, so Bob's chemistry purchase showed as offered today but accepted 19 days ago.
- **Cause:** models took `LocalDateTime.now()` when they were created, so the demo data couldn't be dated in the past.
- **Test:** `SeedDataTest.seedDates_happenInOrder`.

## R2: A seller could never accept a claim *(caught before it was committed)*

- **Area / owner:** shared core (`services/DealRules`).
- **Found:** 2026-11-08, while writing `DealRules`. Never in a commit.
- **Input:** a Live giveaway with one pending claim; the seller accepts that claim.
- **Correct output:** the claim becomes Accepted and the listing Reserved.
- **Output with the flawed check:** refused with "This listing is no longer available".
- **Cause:** "available" (glossary) excludes a giveaway with a pending claim, so checking availability before accepting refuses the very claim being accepted. Acceptance checks that the listing is **Live** instead (Manage Offers & Requests A2).
- **Test:** `DealRulesTest.acceptClaim_worksAlthoughThePendingClaimMakesTheGiveawayUnavailable`.

## R3: Running the tests wrote the app's save file into the project folder

- **Area / owner:** shared core (`data/DataStore`).
- **Found:** 2026-11-08 23:39, while preparing a start-up smoke test. **Fixed:** the same night (app store created lazily).
- **Input:** a new JVM, started in an empty folder, runs `new DataStore(clock)` (what every test does) before anything calls `DataStore.getInstance()`.
- **Correct output:** nothing is written to that folder.
- **Output before the fix:** `marketplace-data/marketplace.dat` (the demo data, about 6 KB) appeared in that folder. During a test run, that folder is the project root.
- **Cause:** the app's store was created in a `static` field of `DataStore`. Java fills static fields the first time the class is used, and `new DataStore(...)` counts as using it.
- **Reproduced:** 2026-11-09, by putting the old eager start-up back: `makingAStore_doesNotTouchTheAppsSaveFolder` failed with *expected: <false> but was: <true>*. It passes again with the fix.
- **Test:** `AppStoreStartupTest.makingAStore_doesNotTouchTheAppsSaveFolder`, which runs the input in a separate JVM because the bug happens only on the first load of the class. A control test, `appStore_createsAndSavesTheDemoDataOnTheFirstRun`, shows the check can see the folder when the app's store really starts.

## R4: Admin's deactivate, delete and hide/unhide review were not saved

- **Area / owner:** **Admin** portal screen (`AdminDashboardController`, Tasks 3.3 and 3.8; admin teammate). The cause came from the shared core: saving was added after the skeleton screen was written. Fixed by Gordon with three one-line changes. **The admin teammate should know about this change.**
- **Found:** 2026-11-09, while checking that every screen changes data inside a transaction. **Fixed:** the same day (three lines in `AdminDashboardController` now use `store.transaction(...)`).
- **Input (in the app):** log in as admin → Manage All Users → Deactivate `bob` (or Seller Ratings → Hide the review) → close the app without doing anything else → start it again.
- **Correct output:** `bob` is still Deactivated and the review is still hidden.
- **Output before the fix:** `bob` was Active again and the review visible. The change showed on screen but was never saved.
- **Reproduced:** 2026-11-09 with the app's real store in a separate JVM. Changed the way the screen did it (`bob.setActive(...)`, `review.setHidden(...)` outside a transaction), it gave `bob active=true, review hidden=false` after the restart. Changed inside `store.transaction(...)`, it gave `bob active=false, review hidden=true`.
- **Cause:** the skeleton screen was written before saving existed, and changed model objects directly. The store saves when a transaction finishes, so changes made outside one wait in memory for the next save and are lost if the app closes first.
- **Test:** `PersistenceTest.adminDeactivationAndReviewHiding_surviveARestart` covers the store path the fixed screen uses. The screen itself has no automated tests, so the input steps above are also a **manual acceptance check**.
- **Rule for everyone:** change model objects only inside `store.transaction(...)`, or through a service that does (README, "Shared Core").

## R5: The seller still saw reviews the admin had hidden

- **Area / owner:** **shared core** (`DataStore` review queries). It *showed up* on the Seller's Reviews & Feedback screen (Task 2.9), and the admin's Hide (Task 3.8) worked correctly, so it is neither an admin nor a seller bug. Fixed only in the shared core; no seller or admin file changed.
- **Found:** 2026-11-09, in manual UI test 9 (storage checklist). **Fixed:** the same day.
- **Input (in the app):** log in as admin → Seller Ratings → Hide the review of charlie → log in as charlie → Reviews & Feedback.
- **Correct output:** the review is not listed. Reviews are "visible to all users unless the admin hides them" (UC10 SR4); a hidden review is "neither shown nor counted" (Manage All User Accounts SR3). Only the admin still sees it, to unhide it.
- **Output before the fix:** charlie still saw the hidden review. His rating correctly left it out.
- **Reproduced:** 2026-11-09, by a test calling `getReviewsForSeller("sam")` (what the seller's screen calls) after hiding one of two reviews: it returned both instead of only the shown one.
- **Cause:** while building the shared core, `getReviewsForSeller` was written to include hidden reviews, "for the seller and the admin". That was an assumption the spec doesn't support, and it undid the skeleton's behaviour, which left hidden reviews out. The seller's screen was moved onto it by a rename.
- **Fix:** `getReviewsForSeller` now leaves out hidden reviews, so every screen that calls it is safe by default. A new `getReviewsForSellerIncludingHidden` is only for the admin's moderation. `getVisibleReviewsForSeller` merged into `getReviewsForSeller`.
- **Test:** `DataStoreTest.hiddenReview_isNotListedForTheSeller`, plus `PersistenceTest.adminDeactivationAndReviewHiding_surviveARestart` (still hidden and not listed after a restart). The input steps above stay a **manual acceptance check**.

## R6: The greeting kept the old name after a profile change

- **Area / owner:** **shared UI** (`ui/ProfileView`, built by Gordon) together with the greeting label in each of the three dashboards. One line changed in each dashboard, the seller's and admin's included. **The seller and admin teammates should know about this change.**
- **Found:** 2026-11-09, while building the shared profile screen (C4); noted as a known gap. **Fixed:** 2026-11-10.
- **Input (in the app):** log in as `alice` → My Profile → change the full name to `Alice Chan-Lee` → Save Changes → look at the greeting at the top right.
- **Correct output:** the greeting reads "Hello, Alice Chan-Lee" as soon as the profile is saved.
- **Output before the fix:** it still read "Hello, Alice Chan" until the next login, although the profile itself was saved correctly.
- **Cause:** each dashboard set its greeting once, when it opened, and the shared profile screen had no way to tell the dashboard that the name changed.
- **Fix:** `ProfileView` takes an action to run after every successful save, and each dashboard passes its own `showGreeting`.
- **Test:** `BuyerAccountScreensTest.profile_newName_isSaved_andTheGreetingShowsItAtOnce` (the buyer's greeting right after Save). The seller's and admin's greetings stay a **manual acceptance check** with the input steps above. That the name itself is saved is covered by `ProfileServiceTest`.

## R7: A listing was hidden when its seller's username was typed in another case

- **Area / owner:** **shared core** (`models/Listing`, `data/DataStore`). It would have shown up in the **Admin** portal's Add Listing (Manage Marketplace Listings 5.6.2), where the admin types the seller's username. That screen is not built yet, so no user met the bug. No seller or admin file changed. **The admin teammate should know:** pass the username to the listing as the admin typed it.
- **Found:** 2026-11-11, while reviewing the shared core against the specification. **Fixed:** the same day.
- **Input:** the seller account `sam` exists and is active. Add a sale listing whose seller username is given as `Sam` (or `SAM`, or ` sam ` with spaces around it) and approve it.
- **Correct output:** the listing is live and available: `isLive` and `isAvailable` are true, and it is listed in `getLiveListings()` (Browse) and in `getListingsBySeller("sam")` (the seller's My Listings).
- **Output before the fix:** the status was Live, but `isLive` and `isAvailable` were false and the listing was missing from Browse; a buyer's card would have said "Live" instead of "Available". Typed as ` sam `, it was also missing from the seller's My Listings.
- **Reproduced:** 2026-11-11, with the two tests below on the unfixed code: *seller typed as 'Sam' ==> expected: <true> but was: <false>* and *expected: <sam> but was: < Sam >*.
- **Cause:** accounts keep their username in lower case and are found ignoring case and surrounding spaces (UC1 A1). `Listing` kept the seller's username exactly as it was given, and `DataStore.isSellerActive` looked the seller up by that exact text. The seller was then "not found", which counts as not active.
- **Fix:** `Listing` keeps the seller's username in lower case without surrounding spaces, as `User` does. `isSellerActive` finds the seller with `findUser`, like every other lookup.
- **Test:** `DataStoreTest.listing_sellerUsernameTypedInAnotherCaseOrWithSpaces_isStillTheSellersLiveListing` and `ModelRulesTest.listing_storesTheSellersUsernameInLowerCaseWithoutSurroundingSpaces`.

## R8: A borrow request could be accepted after its period had ended

- **Area / owner:** **shared core** (`services/DealRules.accept`). It would show up in the **Seller** portal's Manage Offers & Requests (Task 2.5), which is not built yet, so no user met it. No seller file changed. **The seller teammate should know:** accepting such a request now fails with a message, and the screen should show it.
- **Found:** 2026-11-11, while reviewing the shared core against the specification (finding F2). **Fixed:** 2026-11-13, on Gordon's decision to refuse the acceptance.
- **Input:** a Live lend listing; a buyer's borrow request for a 5-second period starting now; the seller accepts it 5 seconds or more later.
- **Correct output:** the acceptance is refused with "The borrow period of this request has already ended. Decline it, or counter with new dates."; the request stays Pending and the listing Live; nobody is notified. Accepted 1 second before the end, it still works.
- **Output before the fix:** the request was accepted and the listing Lent; on the timer's next check (UC12) the item was returned automatically with the due date as its completion date, which was *before* the date it was accepted.
- **Reproduced:** 2026-11-13, by the test below on the unfixed code: 3 of its 4 cases failed with *Expected ServiceException to be thrown, but nothing was thrown*.
- **Cause:** acceptance checked the offer's status and the listing's status, but not whether a borrow period that had already ended could still begin.
- **Test:** `DealRulesTest.acceptBorrowRequest_refusedOnceItsPeriodHasEnded` (boundary values: 1 s before the end, at the end, 1 s and 1 day after).

## R9: A borrowed item looked about 45 seconds late, because its due time was shown to the minute

- **Area / owner:** **shared UI** and the buyer's screens (`ui/Formats`, `ui/DealText`) and the due-date reminder (`services/BorrowingDeadlineService`, `models/BorrowRequest`), all Gordon's. No seller or admin file changed.
- **Found:** 2026-11-14, by Gordon in acceptance scenario AT-UC12-03 (practice data, dev mode): "the auto return came roughly 45 seconds after the due date". **Fixed:** the same day.
- **Input:** the practice *HDMI Cable*, lent to `buyer` from 14:01:44 to 14:06:44 on 14 Nov 2026; My Deals open while the timer runs.
- **Correct output:** My Deals shows "Borrow request: 14 Nov 2026 14:01:44 – 14 Nov 2026 14:06:44" and "Due 14 Nov 2026 14:06:44", and the reminder says "…is due on 14 Nov 2026 14:06:44.", so the return at 14:06:44 is visibly on time.
- **Output before the fix:** "Borrow request: 14 Nov 2026 14:01 – 14 Nov 2026 14:06" and "…is due on 14 Nov 2026 14:06.". Waiting from 14:06:00, the return at 14:06:44 looked 44 seconds late. A 5-second loan even read "14:01 – 14:01".
- **Reproduced:** the save file showed the timer itself was on time: the "returned automatically" notifications are stamped 14:06:44.058, 0.06 s after the due time, and the reminder 0.18 s after its time (UC12 SR1 is met). The tests below failed on the unfixed code, for example *expected: <Borrow request: 14 Nov 2026 14:01:44 – 14 Nov 2026 14:06:44> but was: <Borrow request: 14 Nov 2026 14:01 – 14 Nov 2026 14:06>*.
- **Cause:** borrow times were formatted like every other time, to the minute, although borrowing works to the second (the shortest loan is 5 seconds).
- **Fix:** borrow periods, due dates and the reminder are shown to the second (`Formats.borrowTime`, `BorrowRequest.formatBorrowTime`); messages, notifications and postings stay to the minute.
- **Test:** `DealTextTest.shortLoan_showsItsPeriodAndDueTimeToTheSecond` (the recorded input), `BorrowingDeadlineServiceTest.reminderOfAShortLoan_namesTheDueTimeToTheSecond`, `FormatsTest.period_ofAFiveSecondLoan_showsBothEnds`, `FormatsTest.borrowTimes_areToTheSecond_otherTimesToTheMinute`. AT-UC12-03 stays a manual acceptance check.

---

## R10: The store recorded a report on a listing that does not exist, or "by" an account that is not a buyer

- **Area / owner:** **shared core** (`data/DataStore.addReport`), Gordon's. Found while planning UC9 Report Listing (B7); no screen reached it, because the buyer's service checks first. Jack's admin screens read these reports, so an orphan report would have shown there.
- **Found:** 2026-11-11, in the shared-core review against the spec (finding F5). **Fixed:** 2026-11-22, before UC9 was built.
- **Input:** `addReport(new Report("L999", "bea", OTHER, …))` with no listing L999; and `addReport` on an existing listing with the reporter `sam` (a seller), `ada` (an admin) or `nobody` (no account).
- **Correct output:** each is refused with an `IllegalArgumentException`, and nothing is recorded. The class diagram and UC9 say a buyer files a report on a listing.
- **Output before the fix:** each report was recorded with an id. The new tests failed with *Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.*
- **Cause:** `addReport` only checked for a second report by the same buyer (UC9 SR3), not who files it or on what.
- **Fix:** `addReport` checks that the listing exists and that the reporter is a buyer account before anything is added, as `getOrCreateThread` already did for chats. The service (`ReportService`) checks first and gives the buyer a message, so this inner check only stops programming errors (Lecture 10: the barricade has two layers). Loading a saved file doesn't go through `addReport`, so reports by accounts deleted later still load (client answer #6).
- **Test:** `DataStoreTest.reportOnAnUnknownListing_isRefused`, `DataStoreTest.reportByASeller_anAdmin_orAnUnknownAccount_isRefused`.

---

## R11: Registration refused a weak password without showing the password rules

- **Area / owner:** **shared core** (`services/AuthService`, used by both registrations), Gordon's. Seen on the buyer's register screen; the seller's screen hid it, because it always shows the rules under the field.
- **Found:** 2026-11-11, in the shared-core review against the spec (finding F5). **Fixed:** 2026-11-22, while checking Task 1 against the spec before submission.
- **Input:** register buyer `u4`, full name "Weak Password", password and confirmation `password1`.
- **Correct output:** UC1 A3, "informs the Buyer that the password is invalid or too weak, and shows the password rules": "Password must contain at least one uppercase letter. Password rules: At least 8 characters, with at least one uppercase letter, one lowercase letter, one digit and one special character (such as ! @ # $ %). ASCII characters only." Nothing is stored.
- **Output before the fix:** only "Password must contain at least one uppercase letter." The new test failed with *expected: <… Password rules: At least 8 characters, …> but was: <Password must contain at least one uppercase letter.>*.
- **Cause:** "show the rules" was written once, inside `ProfileService` (UC3 A2), and registration never got it.
- **Fix:** `AccountValidator.validatePasswordShowingRules` gives what is wrong followed by the rules; registration and profile changes both use it, so the two use cases cannot drift apart again. The seller's registration now gives the same message.
- **Test:** `AuthServiceTest.register_weakPassword_saysWhatIsWrong_andShowsThePasswordRules` (the recorded input, buyer and seller), `AccountValidatorTest.aRefusedPassword_isExplainedWithTheRules_anAcceptedOneIsNot`; `ProfileServiceTest` still checks UC3 A2.

---

## R12: The admin's Edit user failed for every seller

- **Area / owner:** **admin** (`services/AdminUserService.editUser`), Jack's. Seen on Manage All Users → Edit.
- **Found:** 2026-11-23, by Jack's test (below); reproduced on 2026-11-24 by Gordon's audit driver (cases E11, E12). **Fixed:** 2026-11-23 (e74efce).
- **Input:** as admin, Edit seller `sam` (preferred locations: Library), full name "Sam Lee", OK.
- **Correct output:** "User updated"; sam's full name is "Sam Lee" and the locations are unchanged.
- **Output before the fix:** nothing saved, no message; `NullPointerException` on the JavaFX thread.
- **Cause:** the null check `locations.contains(null)` throws on the immutable list that `Seller.getPreferredMeetupLocations()` returns (`List.copyOf`), which the screen passes back unchanged.
- **Fix:** check with `locations.stream().anyMatch(Objects::isNull)`, which works for every list.
- **Test:** `AdminUserServiceTest.manageUsers_task33_edit_seller_savesBio_andLocationsInOrderWithoutDuplicates` (it passes a `List.of(...)`).

---

## R13: Hiding a review left the admin's review list unchanged

- **Area / owner:** **admin** (`controllers/admin/AdminDashboardController.showSellerReviews`), Jack's. Seen on Seller Ratings → View Reviews → Hide.
- **Found:** 2026-11-24, in Gordon's review of 4727541 (off-screen reproduction). **Fixed:** 2026-11-25 (dbc1c41).
- **Input:** as admin, Seller Ratings → View Reviews of `charlie` → Hide on bob's review → reason "Spam" → OK.
- **Correct output:** "Review hidden", then the row shows "Hidden" and has no Hide button.
- **Output before the fix:** the review was hidden in the store but the list was not reloaded: `NullPointerException: Cannot invoke "java.lang.Runnable.run()" because "refreshRef[0]" is null`. The Hide button stayed; pressing it again said "This review is already hidden."
- **Cause:** the row's reload used `refreshRef[0]`, which was declared but never assigned.
- **Fix:** `refreshRef[0] = refresh;` after the refresh lambda, as `showUsers` does.
- **Test:** acceptance scenario AT-UC4A-11 (hiding a review updates the list).

---

## R14: Editing a lend listing changed its lending duration

- **Area / owner:** **admin** (`controllers/admin/AdminDashboardController.showListingForm`), Jack's. Seen on All Listings → Edit on a Lend listing.
- **Found:** 2026-11-24, in Gordon's review of 4727541 (off-screen reproduction). **Fixed:** 2026-11-25 (dbc1c41).
- **Input:** a Live lend listing of 36 hours → Edit → change nothing → OK.
- **Correct output:** the lending duration stays 36 hours.
- **Output before the fix:** the form showed "1" days and saved 24 hours. A 10-second listing showed "0" days, and every edit was refused with "The duration must be positive."
- **Cause:** the form pre-filled `getLendingDuration().toDays()` with the unit "days", which drops hours, minutes and seconds.
- **Fix:** the field is left empty when editing (empty keeps the duration), and its prompt shows the current duration.
- **Test:** acceptance scenario AT-UC4A-12 (editing a short lend listing keeps its duration).

---

## R15: In the chat beside the list, the Send button showed only "…"

- **Area / owner:** **shared core** (`ui/ChatView`, the chat's buttons), Gordon's. Seen in the buyer's and the seller's chat since the list of chats moved beside the chat (a315b53).
- **Found:** 2026-11-25, by Gordon in the running app (both portals). **Fixed:** 2026-11-25.
- **Input:** at the app's window size (1200 × 780) or a little wider, open Chats and open any chat.
- **Correct output:** the button reads "Send", and the deal buttons (Make Offer, Agree to Counter, Respond in Offers & Requests) and Search read in full; the message box gets narrower instead. A long listing title takes a second line.
- **Output before the fix:** the button read "…". Reproduced off screen: the new test got the cut texts `[Send]` instead of `[]`. A check of every page of the three portals with the fix to the buttons found one more cut text, in both portals' chats: the chat's title, "Calculus Textbook (Stewart)". No other page cut anything.
- **Cause:** the message box and Send share one row. The chat is narrower beside the list, so the row has less room than both want, and a row shrinks its children down to their minimum width. A button's minimum width by default lets its text be cut to "…".
- **Fix:** the chat's buttons keep the width their text needs (`setMinWidth(Region.USE_PREF_SIZE)`), as the list's Open Chat already did, so the message box gives up the room. The notification board's Mark as read and Mark all as read got the same, for the same kind of row. The chat's title and subtitle wrap onto a second line instead of being cut.
- **Test:** `ChatScreensTest.theChatBesideTheList_cutsNoTextShort_atTheAppsWindowSize` (a chat with a long title; no text on the screen may be cut, `OffScreenApp.cutTexts()`), and `PortalScreensTest`, which now checks every page of the three portals the same way (`cutTextsOnEveryPage()`).

---

## R16: Editing a lend listing, the duration's hint showed whole days again

- **Area / owner:** Admin (`AdminDashboardController`, Edit listing), Jack's. A regression of R14's fix, from PR #24 (83e9b52).
- **Found:** 2026-11-25, in Gordon's review of 83e9b52 (through the screens, off screen). **Fixed:** 2026-11-26 (51a26e7).
- **Input:** as `admin`, All Listings → Edit on a Lend listing whose duration is 36 hours (or 10 seconds).
- **Correct output:** the duration box is empty and its hint reads "Leave empty to keep 1 day 12 hours" ("Leave empty to keep 10 seconds"); Save without typing keeps the duration.
- **Output before the fix:** the hint read "e.g. 1" ("e.g. 0"). Typing what it suggests, "1", and Save turned 36 hours into 1 day: R14's outcome.
- **Cause:** the in-app form of PR #24 wrote the hint from `getLendingDuration().toDays()`, whole days only, instead of R14's `Formats.duration(...)`.
- **Test:** `AdminListingScreensTest.editingALendListing_namesItsDuration_cutsNoText_andKeepsIt`

## R17: The duration's unit read "…" on Add and Edit listing

- **Area / owner:** Admin (`AdminDashboardController`, Add / Edit listing), Jack's. From PR #24 (83e9b52).
- **Found:** 2026-11-25, in Gordon's review of 83e9b52 (a picture of the screen, and `OffScreenApp.cutTexts()`). **Fixed:** 2026-11-26 (51a26e7).
- **Input:** at the app's window size (1200 × 780), as `admin`, All Listings → + Add Listing, or Edit on a Lend listing.
- **Correct output:** the unit box reads "days".
- **Output before the fix:** it read "…"; the test got the cut texts `[days]` instead of `[]`.
- **Cause:** `unitBox.setPrefWidth(100)` fitted the old dialog, but the app's stylesheet pads combo boxes more, so 100 px is too narrow for the text.
- **Fix:** the line is removed; the box takes the width its text needs.
- **Test:** the same test (no text cut on the Edit form).

## R18: A listing's price took any number Java reads, such as "99.999" or "1e3"

- **Area / owner:** Seller (`SellerDashboardController` and the Create/Edit Listing services), Daniel's.
- **Found:** 2026-11-25, in Gordon's review of `main` 2770bd3 (through the screens, off screen). **Fixed:** 2026-11-25 (61d545c).
- **Input:** as a seller, Edit Listing on a Sell listing priced HK$ 80; type the price "99.999"; Save Changes. Create Listing takes the same text.
- **Correct output:** refused with "The price must be a positive number, such as 180 or 99.50."; the price stays HK$ 80 and the form stays as typed, as for the counter price and the admin's listing form (`Amounts.parse`: digits with at most two decimals).
- **Output before the fix:** saved: the price became 99.999, which the cards show as "HK$ 100.00". "1e3" was saved as HK$ 1000.
- **Cause:** the screen read the text with `Double.parseDouble`, which takes exponents and any number of decimals, instead of the shared `Amounts.parse`.
- **Fix:** the screen passes the typed text to `CreateListingService.createSale` and `SellerListingService.updateSale`, which parse it at the service barricade with `Amounts.parse` and throw the same message; the screen's `parsePrice` is gone. The tests pass text, not a `double`.
- **Test:** `SellerScreensTest.r18_aListingPriceIsAPlainAmount_likeEveryOtherPrice`

---

## R19: The registration screens showed their form at the left edge of the window

- **Area / owner:** shared UI: the buyer's registration screen (Gordon) and the seller's (Daniel's), changed together with Gordon's go-ahead for the UI of all three portals. The layout came with the course skeleton (0d1c58a).
- **Found:** 2026-11-26, in the walkthrough screenshots (`docs/walkthrough.md`, shots 02 and 03). **Fixed:** 2026-11-26 (10f9920).
- **Input:** start the app at its default size (1200 × 780); Enter as Buyer, then Register here. The same from Enter as Seller.
- **Correct output:** the form's card in the middle of the window, as on the three login screens.
- **Output before the fix:** the card against the left edge, its middle 369 px left of the window's middle (the seller's 362 px, beside its scroll bar), with empty background on the right.
- **Cause:** the 460-pixel width limit sat on the scroll pane's content instead of on the card. A scroll pane puts its content at the top left, so the 460-pixel content stayed there.
- **Fix:** the scroll pane's content now fills it (`fitToWidth`, `fitToHeight`) and centres the card, which keeps the 460-pixel limit itself. The content's `minHeight` is USE_PREF_SIZE, so a short window still scrolls the form instead of squeezing it (`BuyerRegisterView.fxml`, `SellerRegisterView.fxml`).
- **Test:** `PortalScreensTest.loginAndRegistrationCards_areCentred` (it measures with `OffScreenApp.offCentre`)

---

## R20: Editing a lend listing, the duration's hint was cut to "Leave empty to keep 1 da"

- **Area / owner:** Admin (`AdminDashboardController`, Edit listing), Jack's. Left over from R16's fix (51a26e7), which made the hint name the duration.
- **Found:** 2026-11-26, in Gordon's review of 51a26e7 (a picture of the Edit form). **Fixed:** 2026-11-26 (01eddf7).
- **Input:** at the app's window size (1200 × 780), as `admin`, All Listings → Edit on a Lend listing whose duration is 36 hours.
- **Correct output:** the empty duration box shows its whole hint, "Leave empty to keep 1 day 12 hours", beside the unit box.
- **Output before the fix:** it showed "Leave empty to keep 1 da", so the duration itself could not be read. Reproduced off screen: the test got the cut hints `[Leave empty to keep 1 day 12 hours]` instead of `[]`.
- **Cause:** the box kept a text field's default width, about 12 characters, because its row gave it no more room. A text field cuts its hint off at its edge without "…", so R17's check (`OffScreenApp.cutTexts()`, which reads labels and buttons) could not see it.
- **Fix:** the box takes the row's free width (`HBox.setHgrow(durationField, Priority.ALWAYS)`). The new `OffScreenApp.cutPrompts()` finds the hints of empty text fields that are wider than their field.
- **Test:** `AdminListingScreensTest.editingALendListing_namesItsDuration_cutsNoText_andKeepsIt` (it now also checks `cutPrompts()`)

## R21: The admin's review rows left out the seller's reply

- **Area / owner:** Admin (`AdminDashboardController`, Seller Ratings → View Reviews), Jack's.
- **Found:** 2026-11-26, in Gordon's review of 51a26e7 (through the screens, off screen). **Fixed:** 2026-11-26 (01eddf7).
- **Input:** a completed deal whose buyer reviewed it with "Nice mug" and whose seller replied "Thanks for buying!"; as `admin`, Seller Ratings → View Reviews on that seller.
- **Correct output:** under the comment, "Seller's reply: Thanks for buying!", as on the buyer's screens (the seller's own page shows "Your reply: …"). Spec, Manage Reviews 3.1.5: the reply is shown wherever the review is displayed.
- **Output before the fix:** only the stars, the writer, the item, the deal's date and the comment; the test found no "Seller's reply: Thanks for buying!" on the screen.
- **Cause:** the admin's review row was written without the reply, and it does not use the shared `ReviewList`, which shows it.
- **Fix:** the row adds the shared `ReviewText.reply(review)` under the comment, in the buyer's style (`listing-card-line`), wrapping a long reply instead of cutting it.
- **Test:** `AdminRatingsScreensTest.aReviewWithAReply_showsTheSellersReplyUnderTheComment`

## R22: The admin's user search box was too narrow for its hint

- **Area / owner:** Admin (`AdminDashboardController`, Manage Users), Jack's.
- **Found:** 2026-11-26, by R20's new check of hints, once the check of every page used it too (`PortalScreensTest`). **Fixed:** 2026-11-26 (01eddf7).
- **Input:** at the app's window size (1200 × 780), as `admin`, open Manage Users.
- **Correct output:** the empty search box shows its whole hint, "Search username or name...", with room to spare.
- **Output before the fix:** the hint needed 169.7 px and the box had 169 px inside its padding: at the app's font (Segoe UI, 14 px) the last dot reached the box's edge, and a wider font, as on another system, cuts the hint. The test got `{Manage Users=[Search username or name...]}` instead of `{}`.
- **Cause:** a fixed width, `setPrefWidth(200)`, chosen without the app's font and padding, as with R17's `setPrefWidth(100)`.
- **Fix:** the width is given in characters (`setPrefColumnCount(18)`), so it grows with the font; the hint now has 65 px to spare.
- **Test:** `PortalScreensTest.adminPortal_everyPageOpens`, which checks every page of the admin portal with `OffScreenApp.cutTextsOnEveryPage()`; that method now looks at hints as well (`cutPrompts()`), on every page of the three portals.

## R23: The time boxes of the two borrow forms cut their hint

- **Area / owner:** Buyer (`controllers/buyer/OfferDialogs`, Request Borrow), Gordon's, and Seller (`SellerDashboardController`, Counter borrow request), Daniel's: the same line in each.
- **Found:** 2026-11-26, in the review of the admin fixes: the hint needs 130 px, and the box had 119 px inside its padding. **Fixed:** 2026-11-26.
- **Input:** as a buyer, View Details on a Lend listing → Request Borrow → clear a time box. As a seller, Offers & Requests → Counter on a borrow request → clear a time box.
- **Correct output:** the empty box shows its whole hint, "HH:mm or HH:mm:ss".
- **Output before the fix:** the hint was cut at the box's edge. Both tests got the cut hints `[HH:mm or HH:mm:ss, HH:mm or HH:mm:ss]` instead of none.
- **Cause:** as in R22: a fixed width, `setPrefWidth(150)`, chosen without the app's font and padding.
- **Fix:** the width is given in characters (`setPrefColumnCount(12)`), so it grows with the font; the hint has 27 px to spare. `OffScreenApp.Form.cutPrompts()` checks a dialog's hints; a one-off check of every dialog the test suite opens found no other cut hint.
- **Test:** `MakeOfferScreensTest.borrowRequest_theEmptiedTimeBoxesShowTheirWholeHint`, `SellerScreensTest.counterABorrowRequest_theEmptiedTimeBoxesShowTheirWholeHint`

## R24: In the admin portal, the timer replaced an open form and reset All Listings' filters

- **Area / owner:** Admin (`AdminDashboardController.refreshCurrentScreen`), Jack's.
- **Found:** 2026-11-26, in the review of the admin fixes; the seller's dashboard already guarded against it. **Fixed:** 2026-11-26.
- **Input:** as `admin`, All Listings → + Add Listing → type a title; then the borrowing-deadline timer changes the data (a reminder or a return; in the tests `OffScreenApp.dataChanged()`). Also: All Listings filtered by a seller, then the timer.
- **Correct output:** the form stays as typed. All Listings shows the new data and keeps its filter.
- **Output before the fix:** the form was replaced by a new All Listings page, losing what was typed: the test got the page title "All Listings" instead of "Add Listing". A filtered All Listings came back unfiltered. Edit listing, the user and deal screens and a seller's reviews were replaced the same way.
- **Cause:** the refresh rebuilt the page of the active sidebar button, which stays active while a form or a detail screen is open.
- **Fix:** as in the seller's dashboard: each list page says how it is brought up to date (All Listings runs its search again), and a screen opened with `show(...)`, a form or a detail screen, is left as it is.
- **Test:** `AdminListingScreensTest.aTimerEvent_keepsTheAddListingFormAsTyped`, `AdminListingScreensTest.aTimerEvent_bringsAllListingsUpToDate_andKeepsItsFilter`

## R25: Manage Users' filter bar put "Reg. From:" on one line and its box on the next

- **Area / owner:** Admin (`AdminDashboardController`, Manage Users), Jack's screen. Caused by R22's fix (01eddf7).
- **Found:** 2026-11-26, in the retaken walkthrough screenshot 31. **Fixed:** 2026-11-26.
- **Input:** at the app's window size (1200 × 780), as `admin`, open Manage Users.
- **Correct output:** each filter's label beside its box ("Reg. From:" with its date box, "Reg. To:" with its own).
- **Output before the fix:** R22 widened the search box by 66 px, so the filter bar no longer fitted on one line and wrapped between "Reg. From:" and its date box. The test found the label and the box on different lines.
- **Cause:** the labels and their boxes were separate items of the wrapping filter bar, so a wrap could fall between them.
- **Fix:** each label and its box are one item (`labelled(...)`, as the buyer's Browse filters do with `group(...)`), so the bar wraps between filters, never inside one. The new `OffScreenApp.onOneLine(label, field)` checks it.
- **Test:** `AdminListingScreensTest.manageUsers_eachFilterLabelStaysBesideItsBox`

---

## Tooling notes (not code bugs, so no test)

### T1: `mvnw test` passed on outdated compiled tests

- **Area:** tooling (whole team).
- **Input:** remove a method that only the tests still call (for example `Listing.setStatus`), then run `mvnw test`.
- **Expected:** a compile error in the tests.
- **What happened:** BUILD SUCCESS. Maven's incremental build didn't recompile the unchanged test files and ran the old compiled ones.
- **Prevention:** run **`mvnw clean test`** (the README says so).

### T2: Stop in IntelliJ hangs at "Terminate batch job (Y/N)?"

- **Area:** tooling (Windows + Maven wrapper; whole team).
- **Input:** run the app from IntelliJ's Maven tool window (`javafx:run`, with Maven home set to the wrapper) → click the red Stop button.
- **Expected:** the run ends.
- **What happened:** the console showed `Terminate batch job (Y/N)?` and the run kept going until the tab was closed (2026-11-09, storage UI test 12).
- **Cause:** IntelliJ runs Maven through `mvnw.cmd`, a Windows batch file. Stop sends Ctrl+C, and Windows then asks the batch file whether to stop, waiting for an answer.
- **Workarounds:** type `Y` and press Enter in the Run console; or click Stop a second time, which forces the stop; or set IntelliJ → Settings → Build, Execution, Deployment → Build Tools → Maven → **Maven home path: Bundled (Maven 3)** so no batch file is involved. In normal use, close the app window instead and Maven finishes by itself. Closing the run tab also forces the stop, so it is still a valid "forced stop" for storage test 12.

### T3: Concurrency tests left their threads running when they failed

- **Area:** tests (shared core and Buyer; Gordon's).
- **Found:** 2026-11-21, while fixing IntelliJ's "ExecutorService used without try-with-resources" warning in `DealHistoryServiceTest`.
- **What happened:** five of the six critical-case tests (`DataStoreTest`, `BorrowingDeadlineServiceTest`, `DealRulesTest`, `OfferServiceTest`, `ReviewServiceTest`) wrote their own thread pool and stopped it only after every assertion passed, so a failing test left its threads running in the test JVM.
- **Cause:** the same start-together pattern was written by hand six times; `ExecutorService` can be closed with try-with-resources only from Java 19, and the project targets Java 17.
- **Prevention:** `testsupport/SameMoment` owns the pool and is `AutoCloseable`; every concurrency test uses it with try-with-resources. `SameMomentTest.close_stopsATaskThatIsStillRunning_soNoThreadIsLeftBehind` checks that a running task is stopped.

### T4: The build compiled against JDK 21's library for a Java 17 target

- **Area:** tooling (`pom.xml`; whole team).
- **Found:** 2026-11-21, from the same warning, and from the line every build printed: `[WARNING] system modules path not set in conjunction with -source 17`.
- **What happened:** `maven.compiler.source/target 17` made `javac` check the code against the JDK it ran on (21). An API added after Java 17 (such as `ExecutorService.close()`) would compile and then fail on a Java 17 machine, and IntelliJ suggested such APIs.
- **Prevention:** `maven.compiler.release 17`: the build now compiles against Java 17's own library and rejects anything newer. Everything compiled unchanged, so nothing newer than Java 17 was in use.

---

### T5: IntelliJ's commit check reported 161 errors on code that compiles

- **Seen:** 2026-11-22, committing UC9 (Report Listing): "Commit contains problems: 161 errors and 19 warnings", mostly "cannot resolve" in the new Report classes and the files that use them.
- **Cause:** files created or changed outside IntelliJ while it was open were left out of its project index. `idea.log` showed `ProjectIndexableFilesFilterHealthCheck - Following files are indexable but they were NOT found in filter. Errors count: 9`, naming the 9 new files. Maven built every commit from a clean copy, and all tests passed.
- **Fix:** File > Repair IDE (or Invalidate Caches > Invalidate and Restart). The errors were gone afterwards. A restart alone does not fix it.
- **Avoid:** after files are added outside the IDE, run File > Reload All from Disk (Ctrl+Alt+Y) before committing. If the commit check shows "cannot resolve" while `mvnw clean verify` passes, check `idea.log` before changing any code.

### T6: Two screen tests shared one account without knowing it

- **Area:** tests (`testsupport/ScreenData`, Gordon's); seen between the admin's and the seller's screen tests.
- **Found:** 2026-11-26, in Gordon's trial merge of `main` with SellerDev 3a9194a and Jack's 51a26e7: `SellerScreensTest.theDot_showsAtLogin_onEveryPage_andMarkAllAsReadRemovesIt` expected 1 on the dot and got 2, though each class passed on its own.
- **What happened:** `AdminListingScreensTest` and `SellerScreensTest` both created the seller `dotseller`. All screen tests of a run share one store, and `ScreenData.seller` kept the account that already existed (`DataStore.addUser` returns false for a taken username). So the seller's test found the notice that the admin's test had sent to that account, as an admin's edit of an account does.
- **Cause:** each test was meant to name its accounts in a way no other test does, but nothing checked it.
- **Prevention:** `ScreenData.buyer` and `ScreenData.seller` now fail at once on a taken username, and name it: "The username 'dotseller' is already taken in this run's store: give the test an account of its own". The admin's test now uses `admindotseller`. Every account the screen tests create has a name of its own (73 made with `ScreenData`, and `regbuyer` registered through the screen; checked on 26 Nov).

### Template for a new entry

```markdown
## R<n>: <what went wrong, in one line>

- **Area / owner:** <shared core | Buyer | Seller | Admin | tooling>. Where it shows up, and where the cause is, if different.
- **Found:** <date>, <how>. **Fixed:** <date> (<commit>).
- **Input:** <exact steps or data that make the bug happen>
- **Correct output:** <what should happen>
- **Output before the fix:** <what happened instead>
- **Cause:** <one or two sentences>
- **Test:** `<TestClass.testMethod>`
```
