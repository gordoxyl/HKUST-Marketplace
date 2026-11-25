# HKUST Marketplace — Frontend Demo / Skeleton

A complete **JavaFX + Maven** frontend-only demo of the HKUST Campus Marketplace platform.  
Designed as a **skeleton / starter code** for students to implement the full project requirements (backend, persistence, real business logic, etc.).

## Team Setup (Read This First)

> This README came with the course skeleton. Some of the original instructions below are **outdated for our team's repo**. They are kept for reference and marked **⚠️ Outdated for our team**. Where they conflict with this section, follow this section.

### Requirements
- **JDK 17 or newer** (see [Prerequisites](#prerequisites) below). Maven does **not** need to be installed.
- Check with `java -version` and `javac -version` (PowerShell on Windows, Terminal on macOS).

### Run from the terminal
Run from the repository root (the folder containing `pom.xml`):

| OS | Command |
|----|---------|
| Windows (PowerShell) | `.\mvnw.cmd clean javafx:run` |
| macOS (Terminal) | `./mvnw clean javafx:run` |

- **macOS:** wherever this README shows `.\mvnw.cmd`, use `./mvnw` instead.
- **macOS:** if `./mvnw` says `permission denied`, run `sh mvnw clean javafx:run` instead.

### Run from IntelliJ IDEA
1. Open the repository folder. If IntelliJ offers to load the Maven project, click **Load** (or right-click `pom.xml` → **Add as Maven Project**).
2. Wait for dependencies to finish downloading.
3. Open the **Maven** tool window (right sidebar) → **HKUST Marketplace → Plugins → javafx** → double-click **javafx:run**.

Optional: for a reusable ▶ button, go to **Run → Edit Configurations… → + → Maven**, set Command line to `clean javafx:run`, and leave **Store as project file** unchecked.

**Stopping the app:** close its window, and Maven finishes by itself. On Windows, IntelliJ's red Stop button can leave the console asking `Terminate batch job (Y/N)?`. If so, type `Y` and press Enter, or click Stop again (regression log T2). To avoid the prompt entirely, set **Settings → Build, Execution, Deployment → Build Tools → Maven → Maven home path** to **Bundled (Maven 3)**.

**Do not** right-click `MarketplaceApp.java` → **Run**. It fails with `Error: JavaFX runtime components are missing, and are required to run this application`, because the project has no `module-info.java` and the IDE puts JavaFX on the classpath. Running through Maven avoids this.

### Dev mode: pre-filled logins (DEV-ONLY, remove before release)
Add `-Pdev` to the run command (`.\mvnw.cmd clean javafx:run -Pdev`, or tick the **dev** profile in IntelliJ's Maven tool window). Each login screen is then pre-filled with a test account, so you only click **Login**:

| Portal | Username | Password |
|--------|----------|----------|
| Buyer  | `buyer`  | `Buyer.123`  |
| Seller | `seller` | `Seller.123` |
| Admin  | `admin`  | `Admin.123`  |

The `buyer` and `seller` accounts exist only in dev mode, and dev mode gives `admin` the password above. Without `-Pdev` none of this happens. To remove the shortcut, search the code for `DEV-ONLY`.

## Prerequisites

1. **JDK 17 or newer** (full JDK, not only JRE)
   - Download: https://adoptium.net/ (Temurin 17 or 21 recommended)
   - After install, open a **new** PowerShell and check:
     ```powershell
     java -version
     javac -version
     ```
   - Both commands must work. If `javac` is missing, you only have a JRE — install the JDK.

2. **Maven is optional** — this project includes the **Maven Wrapper** (`mvnw.cmd` on Windows).

## How to Run (Windows)

Open **PowerShell** or **Command Prompt** in the project folder  
(`...\HKUST-Marketplace\HKUST-Marketplace`):

```powershell
.\mvnw.cmd clean javafx:run
```

> **⚠️ Outdated for our team:** our repo has only one `HKUST-Marketplace` folder, so run commands from the repository root (the folder containing `pom.xml`). macOS users: see [Team Setup](#team-setup-read-this-first).

First run downloads Maven automatically (needs internet). Then the app window should open.

### Alternative: if you already installed Maven and added it to PATH

```powershell
mvn clean javafx:run
```

### Run from IntelliJ IDEA

1. Open the project folder as a Maven project
2. Wait for dependencies to download
3. Open `MarketplaceApp.java` → right-click → **Run**

> **⚠️ Outdated for our team:** step 3 fails with `JavaFX runtime components are missing`. Run through the Maven tool window instead. See [Team Setup](#team-setup-read-this-first).

### Run from VS Code

1. Install **Extension Pack for Java**
2. Open the folder
3. Run `MarketplaceApp.java`

> **⚠️ Outdated for our team:** running `MarketplaceApp.java` directly fails for the same reason as in IntelliJ. Use the terminal command in [Team Setup](#team-setup-read-this-first) instead.

## Saved Data

The app keeps its data between runs in the `marketplace-data` folder, which is created in the folder you run the app from (the repository root):

| File | Contents |
|---|---|
| `marketplace.dat` | All accounts, listings, offers, chats, reviews, reports, notifications and admin records |
| `marketplace-audit/<ref>.txt` | Data snapshot of a deleted account, one file per Audit Log entry |
| `marketplace-photos/<id>.img` | One listing photo per file. Encrypted too, so it can't be opened as a picture outside the app |
| `marketplace-dev.dat` | The same as `marketplace.dat`, for dev runs (`-Pdev`) only, so dev accounts never mix with normal data |

- **Encrypted, never plain text.** Every file is encrypted with AES-GCM (`data/DataCipher`); the data file is JSON inside. A file that was edited or damaged is detected and refused. The key is built into the code, so any copy of the app can read the files, and so could anyone with the source code.
- **Saved automatically** after every change. On the first run, or when the folder is missing, the demo data below is created.
- **Unreadable file:** the app keeps it, renamed `marketplace.dat.unreadable-<date>`, starts with the demo data, and shows a warning.
- **To reset to the demo data** (for example before a round of testing), close the app and run, from the repository root:

  | OS | Command |
  |----|---------|
  | Windows | `scripts\reset-data.cmd` |
  | macOS | `sh scripts/reset-data.sh` |

  The script moves the current save files into `marketplace-data/backups/<date-time>/`, so a reset can be undone, and the next start of the app creates fresh demo data. Add `normal` or `dev` to reset only that file, and `-NoBackup` (Windows) or `--no-backup` (macOS) to erase instead of keeping a backup. It refuses to run while the app is open, because the running app would save its data straight back.
- **To add practice data** (deals to try by hand while the seller's screens are not built), close the app and run `scripts\practice-data.cmd` (Windows) or `sh scripts/practice-data.sh` (macOS) from the repository root. It gives `alice` (or `-Buyer <name>`; `buyer` in dev mode) one deal for every button of My Deals, and adds listings in every availability state:

  | Where | What is added |
  |---|---|
  | My Deals, current | Desk Fan and Free Notebooks (accepted: **Mark Complete**), Camera Tripod and HDMI Cable (borrowed: **Return Item**; the cable is due 5 minutes after the script ran: a reminder comes after 2.5 minutes and the timer returns it at 5), Bluetooth Speaker and Camping Tent (countered: **Agree to Counter**), Scientific Calculator (pending) |
  | My Deals, completed | USB-C Hub (not reviewed yet: **Rate Seller**), Umbrella (returned automatically) |
  | Chats only | Office Chair (declined offer) |
  | Browse | Mechanical Keyboard, Yoga Mat, Rice Cooker, Lab Coat (available); Study Desk (Reserved), Projector (Lent), Winter Jacket (Claim Pending) held by another buyer |

  The script does what a seller would do (accept, counter, decline) through the app's own rules, backs the save file up to `marketplace-data/backups/<date-time>/` first, and refuses to run while the app is open. It adds nothing if practice data is already there; add `-Force` for another set, or run the reset script to start over. The tool is `testsupport/PracticeData` in the test sources, so it is not part of the app.
- The folder is in `.gitignore`. Don't commit it or put it in the submission ZIP: each machine makes its own on the first run.

## Demo Credentials

| Role   | Username  | Password     |
|--------|-----------|--------------|
| Admin  | `admin`   | `Admin@1234` |
| Buyer  | `alice`   | `Demo@1234`  |
| Buyer  | `bob`     | `Demo@1234`  |
| Seller | `charlie` | `Demo@1234`  |
| Seller | `diana`   | `Demo@1234`  |

All passwords meet the password rules: at least 8 characters, with at least one uppercase letter, one lowercase letter, one digit and one special ASCII character (such as `! @ # $ %`). Usernames and passwords may contain only ASCII characters.

## Features Implemented

### Shared
- Welcome screen with role selection (Buyer / Seller / Admin)
- HKUST branding (logo + colour palette: deep blue `#003974` + gold `#A68137`)
- Modern card-based UI, icons (Ikonli), consistent sidebars & top bars
  - **⚠️ Outdated for our team:** Ikonli is not in `pom.xml` and is not used by the code.
- All data in one store, saved to an encrypted file on this computer after every change (no database server, no network); see [Saved Data](#saved-data) and [Shared Core](#shared-core-read-before-building-a-feature)
- **Notification Board (UC4), the same in all three portals:** newest first, each with its category, content and time; unread ones marked, with Mark as read on each and **Mark all as read** while anything is unread; a **category filter** offering the categories on the user's board
- **Unread dot:** a red dot with the number of unread notifications on the sidebar's Notifications button (buyer portal; the seller and admin portals add it with two lines, see the guides). Only marking notifications as read lowers it
- **Chat screen (UC6), the same for buyers and sellers:** the user's chats in a list beside the open chat, with a light grey line between them, newest first, with the open chat marked; the list can be hidden and shown again. Every chat has Back above it: **Back to Chats** when chosen on the Chats page, otherwise back to the screen it was opened from

### Buyer Portal
- Registration, Login
- **Browse Marketplace (UC8, done):** search by keyword; filter by category, type, condition and price range; sort; listing detail with photos, all fields, seller rating and reviews; seller profile; view count
- **Report Listing (UC9, done):** from the listing detail, report the listing to the admin with one of six reasons and the details, once per listing; every admin is notified, and the seller is never told who reported
- **Make Offer (UC5, done):** price offer, borrow request with start and end date and time, one-click claim; from the listing detail, from the chat, or by agreeing to a seller's counter
- **My Deals (UC7, done):** current and completed deals with status, terms, counter and due date; mark a purchase or claim complete; return a borrowed item before its due date; agree to a counter; reopen the listing or the chat
- **Review Seller (UC10, done):** once a deal is completed or a borrowed item returned (by the buyer or the timer), rate the seller from 1 to 5 stars with a written review, once per deal, from the prompt that follows or the deal's Rate Seller button on My Deals; the seller is notified
- **Borrowing deadlines (UC12, done):** while the app runs, a timer reminds buyers 24 hours before an item is due (halfway for periods under 48 hours) and returns items automatically at their due date; the open screen updates by itself
- **Deal History (UC11, done):** every completed deal with its item title, seller, deal date, deal type (Bought / Borrowed / Received) and outcome (Completed / Returned / Auto-returned), newest first; filter by a date range and a category and search by the seller's name; it updates by itself when the timer returns an item
- Chats, Notifications, Profile

### Seller Portal
- Registration, Login
- **Create Listing (Task 2.3, done):** Sell, Lend or Give Away, with category, condition, description, meet-up location, a price or a lending duration (5 seconds or more), and up to 6 photos; submitted for the admin's review
- **My Listings (Task 2.4, done):** every listing with its status; edit and delete. An edited Live listing with no open offers stays Live (client answer #5). Any other edit goes back for review, and open offers are declined with their buyers told
- **Offers & Requests (Task 2.5, done):** every offer, borrow request and claim on the seller's listings; accept, decline, or counter with a price or a borrow period (to the second); open the linked chat. A chat's "Respond in Offers & Requests (n)" opens this screen with that chat's offers marked
- **Seller Stats (Task 2.8, done):** views, offers received, completed deals and rating; a bar chart of views and offers per listing, and a pie chart of completed deals by type
- **Reviews & Feedback (Task 2.9, done):** every review with its writer, listing and date; reply once (the buyer is notified) and flag an inappropriate review for the admin
- Chats, Notifications, Profile

### Admin Portal
- Login (the seeded `admin` account)
- **Listing Review (Task 3.2, done):** every listing pending review; approve it, or reject it with a reason, and the seller is told
- **Manage All Listings (Task 3.7, done):** every listing in any status; filter by status (Reported included), category, type, seller and date; view the details with the reports; edit; add a listing for a seller (Live at once, with photos); remove one with a reason, which declines its open offers and tells their buyers and the seller
- **Manage Users (Task 3.3, done):** search by username or name; filter by role, status and registration date; view a user's details; edit; deactivate with a reason and reactivate; delete with a reason, recorded in the Audit Log
- **All Deals Record (Task 3.5, done):** every accepted deal with its type, start and return/complete dates and status (client answer #4); filters; details; export to CSV
- **Seller Ratings (Task 3.8, done):** each seller's average rating and completed deals; their reviews, filtered by rating and date; hide a review with a reason, and the buyer and the seller are told
- **Notification Board (Task 3.6, done):** the accounts' history (every account change reaches every admin, client question F4), new listings waiting for review and reported listings; Profile

## Project Structure

```
HKUST-Marketplace/
├── mvnw / mvnw.cmd          ← Maven Wrapper (use these!)
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/hkust/marketplace/
    │   ├── MarketplaceApp.java
    │   ├── controllers/     ← JavaFX screens, one package per portal
    │   ├── data/            ← DataStore (all data, thread-safe) and SeedData (demo data)
    │   ├── models/          ← domain classes from the class diagram
    │   ├── services/        ← logic shared by the portals: AuthService, AccessControl, ...
    │   └── utils/           ← SceneManager, SessionManager
    └── resources/
        ├── fxml/
        ├── css/styles.css
        └── images/hkust_logo.png
```

## Shared Core (read before building a feature)

The use-case specification is authoritative for behaviour, except where the client (TA) answered our questions differently: those answers, with what each one changes and who does it, are in [docs/client-answers.md](docs/client-answers.md).

The model follows the domain class diagram in our use-case specification:

| Base class (abstract) | Subclasses | Notes |
|---|---|---|
| `User` | `Buyer`, `Seller`, `Admin` | `getRole()` gives the account type. Usernames are lower case and cannot change. |
| `Listing` | `SaleListing` (price), `LendListing` (`Duration`), `GiveawayListing` | Up to 6 photos; the first is the thumbnail. New listings are Pending Review. Change status only with the state machine events (`approve`, `reject`, `resubmit`, `withdraw`, `remove`, `acceptDeal`, `completeDeal`), which refuse invalid changes. |
| `Offer` | `PriceOffer`, `BorrowRequest`, `Claim` | Called a "deal" in the use cases. Change status only with `accept`, `decline`, `counter`, `complete`, which refuse invalid changes. |

Both state machines, with the use case and role behind every event, are in [docs/state-machines.md](docs/state-machines.md).

Other classes: `ChatThread` + `Message`, `Review`, `Report`, `Notification`, `ListingActionRecord`, `AuditLogEntry`, and enums such as `Category`, `MeetupLocation` and `NotificationCategory` (their `toString()` is the display name, so they can go straight into a `ComboBox`). `UserRole` is shown with `getDisplayName()` ("Buyer"); its `toString()` stays the constant's name ("BUYER"), which the Audit Log's account snapshots record.

**Getting data.** Use `DataStore.getInstance()`. It already implements the shared rules, so reuse them instead of rewriting them:
- `getLiveListings()`, `isLive(listing)`, `isAvailable(listing)`: the glossary's "live" and "available" listings
- `addOffer(offer)`: checks the offer type matches the listing type and creates the chat thread
- `getReviewsForSeller(seller)`, `getAverageRating(seller)`: leave out reviews the admin hid, because hidden reviews are shown to no one (UC10 SR4). Only the admin's moderation screen uses `getReviewsForSellerIncludingHidden(seller)`
- `getRating(seller)`: the seller's rating as one `RatingSummary` (count and average, which always agree: no average without ratings). Show it with `Formats.averageRating(rating)`.
- `addReview`, `addReport`, `addUser`: refuse a second review of a deal, a second report by the same buyer, and a taken username. `addReport` also refuses a report on a listing that doesn't exist, or by an account that isn't a buyer (regression log R10)
- A deleted listing is not removed from the store: the seller's delete is `listing.withdraw()` and the admin's removal is `listing.remove()`, both giving status Removed.
- `getTotalViews(seller)`, `getOffersReceivedCount(seller)`, `getCompletedDealCount(seller)`: the seller's statistics (the derived attributes in the class diagram), for Seller Stats and the admin's Seller Ratings. With `getAverageRating` and `getRatingCount`, that is all five.
- `deleteAccount(username)`: the data step of the admin's Delete User, all at once. It deletes the account, its chat threads and its notifications; declines its open offers and those on its listings; and sets its listings to Removed. Deals, reports and the reviews the account wrote stay as records ([client answer #6](docs/client-answers.md)), and the username can never be registered again. It returns a `DeletedAccount` with what was affected and a text `snapshot()` for the Audit Log entry. Notifying the affected users and writing the Audit Log entry are the admin use case's job.
- `getReviewerName(review)`: the name a screen shows for a review's writer: "Deleted User" once that buyer's account is deleted, otherwise the buyer's full name. A kept review still counts in the seller's rating.
- `addPhoto(bytes)` and `readPhoto(id)`: listing photos, each kept encrypted in a file of its own. Use `PhotoService` and `PhotoFrame` (below) instead of calling these directly.
- `read(() -> ...)`: several reads as one consistent step, without saving.

**Shared services: reuse these, don't rewrite them.**
- `DealRules`: the deal lifecycle that crosses roles. `accept(offer)` (Reserved or Lent, declines the other open offers, notifies; refuses a borrow request whose period has already ended), `declineOpenOffers(listing, category, because)` (for edits, deletes, admin removal, deactivation), `complete(deal)`, `returnItem(request, automatic)`. Check the role first, then call these.
- `NotificationService`: `send(user, category, text)` and `sendToAdmins(category, text)` when something happens; `getMyNotifications()`, `markRead(id)`, `markAllRead()` (one transaction) and `getUnreadCount()` for the board (UC4); `getMyCategories()` and `getMyNotifications(category)` for its category filter. Sending to an account the admin deleted is skipped, not an error.
- `ChatService`: UC6 Chat About Listing, for buyers and sellers. `openForListing(listingId)` (buyer, from the listing detail), `openForOffer(offerId)` (buyer from My Deals, seller from an incoming offer), `getMyConversations()` (the Chats screen), `send(chat, text)` and `search(chat, keyword)`. It checks on every call that the user is the chat's buyer or the listing's seller, refuses empty messages, and notifies the other party. Offers appear in the history alongside the messages.
- `BrowseService` (buyer, UC8): `search(BrowseQuery)` for the live listings by keyword, filters, price range and sort order; `openListing(id)`, which adds one view and returns a `ListingDetail`; `getSellerProfile(username)`. For prices a giveaway counts as HK$0 and lend listings have none.
- `OfferService` (buyer, UC5): `start(listingId)` or `startFromCounter(offerId)` gives the `OfferForm` to show, then `submitPriceOffer(listingId, typedPrice)`, `submitBorrowRequest(listingId, start, end)` or `submitClaim(listingId)`. The final availability check and the recording are one transaction. A listing that is no longer available throws `ListingUnavailableException`; the seller gets an Offer Received notification.
- `BorrowingDeadlineService` and `BorrowingDeadlineTimer` (UC12): the app starts the timer in `MarketplaceApp`; every 0.5 s it sends due-date reminders and returns items whose due date has come, through `DealRules.returnItem`. Nothing to call from a screen.
- `MyDealsService` (buyer, UC7): `getMyDeals()` gives each `Deal` with what can be done with it (`canBeMarkedComplete`, `canBeReturned`, `hasCounter`, `canBeReviewed`, `dueDate`); `markComplete(offerId)` and `returnItem(offerId)` check ownership, type, state and the due date, then use `DealRules`.
- `DealHistoryService` (buyer, UC11): `find(HistoryQuery)` gives the buyer's completed deals as `HistoryEntry` rows (deal, listing, seller's name, `dealDate()`), newest deal date first, filtered by a date range (both ends included), a category and part of the seller's name. A start after the end throws an `InvalidDateRangeException` (a `ServiceException`; A2), which the screen shows in red under the dates instead of in a dialog. It only reads.
- `ReviewService` (buyer, UC10): `start(offerId)` gives the `ReviewForm` for a completed deal not reviewed yet; `submit(offerId, rating, comment)` records the review and notifies the seller, in one transaction. A deal that can't be reviewed throws `DealNotReviewableException` (the use case ends); a missing rating or written review throws a plain `ServiceException` (the buyer corrects the form).
- `ReportService` (buyer, UC9): `start(listingId)` gives the `ReportForm` (the listing and the six reasons) for a listing the buyer can open and hasn't reported; `submit(listingId, reason, details)` records the report and notifies every admin, in one transaction. A listing that can't be reported (reported already, or no longer open to the buyer) throws `ListingNotReportableException` (the use case ends); a missing reason or details throws a plain `ServiceException` (the buyer corrects the form). Which listings a buyer can open is one rule, `ListingAccess`, shared with `BrowseService`.
- `Amounts`: `parse(typedText)` reads a typed amount of money (digits with at most two decimals) and `plain(amount)` writes one for an input box. Use it wherever a price is typed.
- `PhotoService`: `importPhoto(file)` copies a PNG, JPEG, GIF or BMP image into the app's encrypted storage and returns the photo's id for `listing.setPhotos(...)`. For sellers and the admin. Import when the listing is submitted, so a cancelled form stores nothing.
- `AuthService`: registration and login (UC1, UC2).
- `ProfileService`: UC3 Manage Profile. `updateProfile(name, newPassword, confirm)` for everyone, and `updateSellerProfile(..., bio, locations)` for sellers. A blank new password keeps the current one; the username and residence hall can't change.

**Shared UI kit (`ui/`): use it on every screen, so the three portals look and behave the same.**
- `Formats`: dates (`09 Nov 2026 14:05`), prices (`HK$ 180`), stars (`★★★★☆`), average ratings (`4.5 / 5 (2 ratings)` or `No ratings yet`), durations, and borrow times to the second (`borrowTime`, `period`: `14 Nov 2026 14:06:44`), because a loan can be as short as 5 seconds. A date picker shows and reads its date in the same form (`21 Nov 2026`) with `picker.setConverter(Formats.datePickerConverter())`, as Deal History and the borrow-request form do.
- `Dialogs`: `confirm` (ask before every destructive action), `info`, `error`, `problem(ServiceException)`, and `askReason` (asks again until a reason is typed). `TextInputs.dateTime(date, typedTime)` joins a date picker's date and a typed time (`14:30` or `14:30:05`).
- `NotificationBoard` (UC4): the notification board all three portals show, with newest first, an unread count, Mark as read, Mark all as read (while anything is unread) and a category filter. Open it with `show("Notification Board", new NotificationBoard(new NotificationService()))`.
- `NotificationDot`: the red dot with the number of unread notifications on the sidebar's Notifications button. A dashboard adds it with two lines: `NotificationDot.attach(btnNotifications, new NotificationService())` in `initialize()`, and `NotificationDot.update()` in its `show(title, content)`. The board and `LiveUpdates` update it too. Opening the board does not lower it; only marking as read does (UC4 Special Requirement 3).
- `ProfileView` (UC3): the profile screen all three portals show. Sellers also get bio and meet-up locations; buyers see their residence hall, which can't be changed.
- **Buttons beside a field that grows** (a message box, a search box) keep the width their text needs, `button.setMinWidth(Region.USE_PREF_SIZE)`; otherwise a narrow row cuts them to "…" (regression log R15). The screen tests check every page at the app's window size with `OffScreenApp.cutTexts()`.
- `ListingCard`: the listing card all three portals show (Browse, My Listings, Listing Review, Manage All Listings). `new ListingCard(listing, UserRole.BUYER)` (or `SELLER`, `ADMIN`), then `addActions(buttons...)` for the screen's own buttons and `setOnOpen(...)` for a click on the card; put several in `ListingCard.grid()`. Buyers see the availability status and unavailable listings greyed out (UC8); sellers and the admin see the status. What each line says comes from the tested `ListingSummary`.
- `PhotoFrame`: shows one stored photo in a frame of a fixed size, or "No photo": `new PhotoFrame(photoId, 240, 180)`. The listing card uses it for the thumbnail, and `ListingOverview` for a detail screen's photos.
- `ListingOverview`: the top of a listing's detail screen, the same in every portal: all photos, the title with the status badge and price, every field and the description. `new ListingOverview(ListingSummary.of(listing, UserRole.ADMIN, store), store)`; add the screen's own parts below it, each framed with `ListingOverview.card(node)`. The buyer's detail screen uses it; the admin's View listing should too.
- `ChatList` and `ChatView` (UC6): the chat screen, the same for buyers and sellers: the user's chats in a list, newest first, and the open chat (`ChatView`) beside it, marked in the list; a toggle hides and shows the list. The Chats page is `show("Chats", new ChatList(chatService, this, onOpen))`; a chat opened from another screen is `new ChatList(chatService, this, onOpen).openFrom(chat, "Back to …", back)`. Either way the chat has Back above it: "Back to Chats" from the Chats page, otherwise back to where it was opened. `onOpen` adds the portal's own options to every chat. The view shows messages and offers with sender and time, sends on Enter, and searches the history (a result jumps to its message and highlights it). The deal actions are buttons in one row under Send, the same in both portals (the offer cards only show history): the buyer portal adds Make Offer with `chatView.setOnMakeOffer(...)` and Agree to Counter with `setOnAgreeToCounter(...)`; the seller portal adds the link Respond in Offers & Requests (n) with `setOnRespondToOffers(...)`, which opens Offers & Requests with the chat's offers marked, because the seller answers offers only there, Task 2.5 (contract in `docs/shared-core-for-seller.md`). Which offers allow which action is decided by `services/OfferAction`.
- `SubScreen`: a screen opened from another one, with a Back button above it: `show(title, new SubScreen("Back to listings", back, content))`. Back shows the earlier screen as it was.
- `ReviewList` and `ReviewText`: a seller's average rating and reviews (stars, date, comment, the seller's reply), as shown on the listing detail screen and the seller's profile. Pass the reviews from `getReviewsForSeller`, which already leaves out hidden ones.
- `ListingSummary` also words a detail screen: `fields()` (every field of the listing as name and value), `descriptionText()` and `actionText()`; `ListingOverview` lays them out.
- `OfferText`: how an offer reads everywhere: `summary(offer)` (`Price offer: HK$ 160`), `counter(offer)`, `status(offer)`, the status badge's style, `actionText(type)` (Make Offer / Request Borrow / Claim Item) and `submitted(offer, title)` for the confirmation. Use it in My Deals, the seller's offers and the admin's deal records.
- `DealText`: the wording of My Deals: seller and type, the dates a deal went through, the due date, and the confirmation questions.
- `HistoryText`: the wording of Deal History: the deal type from the buyer's side (Bought / Borrowed / Received) and the line above the table (`1 deal`, or the no-deals message).
- `ReportText`: the wording of the report form (UC9): its heading, the hint under the fields, and the confirmation.
- `Choices`: filter boxes that start with "All …": `Choices.withAll("All categories", Category.values())`, and `Choices.chosen(box, Category.values())` for the chosen value or null. Used by Browse, Deal History and the notification board.
- `LiveUpdates` and `Refreshable`: when the timer changes data, the open dashboard reloads its screen, and the `NotificationDot` is updated. A dashboard registers once in `initialize()` with `LiveUpdates.setOnDataChanged(() -> contentPane.getScene() != null, this::refresh…)`; screens that can reload implement `Refreshable` (the notification board, chats, Browse, My Deals do). The seller's My Listings can use it to show a returned item Live again.
- `ContentHost`: implemented by each dashboard as `show(title, content)`. Shared components get the dashboard as a `ContentHost`, so they can open another screen with its data, such as a chat from the Chats screen.

**How a service method is shaped.** One control class per actor and use case (Lecture 15), each method in this order:
```java
public void acceptOffer(String offerId) {
    Seller seller = AccessControl.requireSeller();                  // 1. role check
    store.transaction(() -> {                                       // 2. one atomic step
        Offer offer = store.findOffer(offerId);
        if (offer == null || !store.getListingOf(offer).getSellerUsername().equals(seller.getUsername())) {
            throw new ServiceException("This offer is no longer available.");   // 3. validate (the barricade)
        }
        dealRules.accept(offer);                                    // 4. shared rules
    });
}
```
The screen calls the service and shows any `ServiceException` message to the user as it is. `AccessDeniedException` is one too. Screens don't call `DataStore` directly in new code; the skeleton dashboards still do until each feature is rebuilt.

**Several steps as one.** Wrap them in `store.transaction(() -> { ... })` so another thread cannot run in between. Use it when you check something and then change data based on it, and when you change model objects you got from the store.

**Role checks.** Start every portal-specific service method with `AccessControl.requireBuyer()`, `requireSeller()` or `requireAdmin()` (UC2 Special Requirement 3). They return the logged-in user, or throw `AccessDeniedException`.

**Time.** Never call `LocalDateTime.now()`; use `store.now()`. The store dates records when they're added (posted date, offer date, timestamps), and tests replace its clock.

**Storage.** `DataStore` saves everything after every change and loads it at start-up, so keep all data access going through it. Nothing else needs to know about files. See [Saved Data](#saved-data).

## How We Build and Test

We use **sandwich integration** (COMP 3111 Lecture 14). The bottom layer (models and `DataStore`) and the top layer (the portal screens from the skeleton) already exist, and we meet in the middle:
- **The shared middle** (`DealRules`, `NotificationService`, the state machines and the conventions above) is built first, bottom-up, and tested with JUnit tests acting as drivers. It holds the critical subsystems: the deal lifecycle, concurrency and the borrowing-deadline timer.
- **Each portal** (buyer, seller, admin) is then built in parallel by its owner, one use case at a time: control class plus tests, then the screen.

| Level | What | Where |
|---|---|---|
| Unit | Equivalence partitions and boundary values (e.g. rating 0/1/5/6, photos 6/7, borrow time 4 s/5 s); state-based tests for each state machine | `src/test/.../models`, `AccountValidatorTest` |
| Integration | Use-case flow tests at service level, including flows across roles (offer → accept → complete → review; buyer and seller chatting; the rest of the system after an account is deleted); run all tests again after every change | `DealRulesTest`, `ChatServiceTest`, `AccountDeletionFlowTest`, `DataStoreTest`, `PersistenceTest` |
| Regression | Every bug found gets a test that reproduces it, with its input and correct output recorded in [docs/regression-log.md](docs/regression-log.md) | e.g. `AppStoreStartupTest` |
| System | Screen tests: the real screens, in a window nobody sees. A demo user of each portal logs in on the welcome screen and opens every page. Every buyer use case runs through its screens with its alternative flows: forms are filled in and submitted or cancelled as the test says, and other dialogs are answered Cancel. A test that changes data has accounts and listings of its own | `PortalScreensTest`, `controllers/buyer/*ScreensTest` |
| Acceptance | One manual scenario per flow of each use case, run through the screens; teammates test each other's portals | [docs/acceptance-tests.md](docs/acceptance-tests.md) (UC4's admin part and UC5 to UC12 so far) |

Which course concept is applied where (boundary/control/entity classes, the barricade, equivalence partitions, boundary values, basis paths, thread and state-based tests) is listed in [docs/course-concepts.md](docs/course-concepts.md).

Test tools in `src/test/.../testsupport`: `TestClock` (a clock the test moves by hand, so a 5-second borrow is tested instantly) and `Fixtures` (accounts and listings in any state, built through the state machine). `OffScreenApp` runs the app's screens for the screen tests: it presses buttons, types into fields, answers dialogs and reads what the screens show. `ScreenData` adds a screen test's own accounts, listings and deals to the data the screens use. `TestDataFolder` gives every test run a new temporary data folder, so the screen tests use the demo data there and never the project's `marketplace-data`. `StoreProbe` is a tiny program a test runs in a separate JVM. Run all tests with `.\mvnw.cmd clean test`: without `clean`, Maven can run outdated compiled tests (regression log T1).

## Reports

One command runs all tests and produces every report the submission needs:

| OS | Command |
|----|---------|
| Windows | `.\mvnw.cmd clean verify` |
| macOS | `./mvnw clean verify` |

| Report | Open in a browser |
|---|---|
| Unit-test report (every test, pass/fail) | `target/reports/surefire.html` |
| Coverage of the measured code (>65% branch rule) | `target/reports/jacoco/index.html` |
| Coverage of everything, screens included (>65% branch rule) | `target/reports/jacoco-all/index.html` |
| JavaDoc | `target/reports/apidocs/index.html` |

The build **fails** if any test fails, or if **branch coverage drops below 65%** in either coverage report. `target/` is not committed, so run the command again to get fresh reports. JavaDoc checks links, HTML and syntax, but not missing `@param`/`@return` tags on simple methods.

**In IntelliJ:** right-click `src/test/java` → **Run 'Tests in java' with Coverage**. It runs the same tests, the screen tests included, so the screens are measured too. IntelliJ counts branches a little differently from JaCoCo, so its figures can differ slightly. After pulling a change to `pom.xml`, click **Load Maven Changes** first.

**Coverage policy.** The submission asks for ">65% branch coverage" without saying of which code, so the build checks it twice:
- **Everything**, screens included: the strict reading. The screen tests keep the screens covered; new screen code without tests lowers this figure.
- **The measured code**: the models, data, services and utils, where all the logic lives. It leaves out the JavaFX screen controllers, `MarketplaceApp`, `SceneManager` and the dev-only `DevLogin` (the excluded list is `jacoco.ui.excludes` in `pom.xml`). Screens hold no logic of their own: they read the input, call a tested service and show the result. The screen tests open every page, and the manual acceptance tests check the flows. That only stays true if **screens stay thin**: any check, rule or calculation goes into a service with tests, never into a controller.

Figures on 25 Nov 2026, with all three portals and the shared UI changes merged (PR #20 to #25): 1297 tests; measured code **96.7% branch** (97.8% line); everything, screens included, **80.6% branch** (81.8% line). By package: services 96.8%, models 98.7%, data 92.4%, shared screens (`ui`) 92.2%, buyer screens 93.5%. The seller's and admin's screens are at 16.2% and 21.1%: the screen tests only open their pages, and their logic is in their tested services.

## Troubleshooting

### `mvn` is not recognized
Use the wrapper instead:
```powershell
.\mvnw.cmd clean javafx:run
```

### `java` / `javac` not recognized
Install JDK 17+ from https://adoptium.net/ and **restart the terminal**.  
Make sure the installer option “Add to PATH” was checked.

### Build fails with JavaFX / DISPLAY errors
You need a normal desktop session (not a headless server). On Windows this is usually fine. The screen tests open a window nobody sees (transparent, outside the screen) for a few seconds; it may take the keyboard focus while they run. Without a display they are skipped, not failed. The warning `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'` in the test output is harmless.

### To see the real error
```powershell
.\mvnw.cmd clean javafx:run -e
```
Copy the lines that start with `Exception in thread` or `Caused by`.

## For Students — Next Steps (Backend)

> **⚠️ Outdated for our team:** items 1–4 are done: `DataStore`, PBKDF2 password hashing, `AccessControl`, and an encrypted save file instead of a database (see [Saved Data](#saved-data)).

1. Replace `DummyDataService` with real repositories / REST clients
2. Hash passwords (BCrypt) — never store plaintext
3. Add JWT / session auth and enforce RBAC on the API
4. Persist data in a database
5. Photo upload, chat, auto-return for borrowed items, etc.

## Licence

Educational use — HKUST COMP project skeleton.
