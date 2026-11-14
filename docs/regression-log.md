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
- **Test:** none automated: this is a screen-only fault, and screens have no automated tests (README, "Reports"). The input steps above are a **manual acceptance check**, for all three portals. That the name itself is saved is covered by `ProfileServiceTest`.

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

---

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
