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

## Demo Credentials

| Role   | Username  | Password     |
|--------|-----------|--------------|
| Admin  | `admin`   | `Admin@1234` |
| Buyer  | `alice`   | `Demo@1234`  |
| Buyer  | `bob`     | `Demo@1234`  |
| Seller | `charlie` | `Demo@1234`  |
| Seller | `diana`   | `Demo@1234`  |

All passwords meet the password rules: at least 8 characters, with at least one uppercase letter, one lowercase letter, one digit and one special ASCII character (such as `! @ # $ %`). Usernames and passwords may contain only ASCII characters.

## Features Implemented (UI Only)

### Shared
- Welcome screen with role selection (Buyer / Seller / Admin)
- HKUST branding (logo + colour palette: deep blue `#003974` + gold `#A68137`)
- Modern card-based UI, icons (Ikonli), consistent sidebars & top bars
  - **⚠️ Outdated for our team:** Ikonli is not in `pom.xml` and is not used by the code.
- In-memory data (no database / no network); see [Shared Core](#shared-core-read-before-building-a-feature)

### Buyer Portal
- Registration, Login, Browse Marketplace, Make Offer / Borrow / Claim
- My Deals, Deal History, Favourites (placeholder), Notifications, Profile

### Seller Portal
- Registration, Login, Create Listing, My Listings
- Seller Stats (charts), Reviews & Feedback, Notifications, Profile

### Admin Portal
- Login only (seeded), Listing Review (approve/reject)
- Manage Users, All Listings, All Deals, Seller Ratings, Notifications, Profile

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

The model follows the domain class diagram in our use-case specification:

| Base class (abstract) | Subclasses | Notes |
|---|---|---|
| `User` | `Buyer`, `Seller`, `Admin` | `getRole()` gives the account type. Usernames are lower case and cannot change. |
| `Listing` | `SaleListing` (price), `LendListing` (`Duration`), `GiveawayListing` | Up to 6 photos; the first is the thumbnail. New listings are Pending Review. Change status only with the state machine events (`approve`, `reject`, `resubmit`, `withdraw`, `remove`, `acceptDeal`, `completeDeal`), which refuse invalid changes. |
| `Offer` | `PriceOffer`, `BorrowRequest`, `Claim` | Called a "deal" in the use cases. Change status only with `accept`, `decline`, `counter`, `complete`, which refuse invalid changes. |

Both state machines, with the use case and role behind every event, are in [docs/state-machines.md](docs/state-machines.md).

Other classes: `ChatThread` + `Message`, `Review`, `Report`, `Notification`, `ListingActionRecord`, `AuditLogEntry`, and enums such as `Category`, `MeetupLocation` and `NotificationCategory` (their `toString()` is the display name, so they can go straight into a `ComboBox`).

**Getting data.** Use `DataStore.getInstance()`. It already implements the shared rules, so reuse them instead of rewriting them:
- `getLiveListings()`, `isAvailable(listing)`: the glossary's "live" and "available" listings
- `addOffer(offer)`: checks the offer type matches the listing type and creates the chat thread
- `getAverageRating(seller)`, `getVisibleReviewsForSeller(seller)`: leave out reviews the admin hid
- `addReview`, `addReport`, `addUser`: refuse a second review of a deal, a second report by the same buyer, and a taken username
- A deleted listing is not removed from the store: the seller's delete is `listing.withdraw()` and the admin's removal is `listing.remove()`, both giving status Removed.

**Shared services: reuse these, don't rewrite them.**
- `DealRules`: the deal lifecycle that crosses roles. `accept(offer)` (Reserved or Lent, declines the other open offers, notifies), `declineOpenOffers(listing, category, because)` (for edits, deletes, admin removal, deactivation), `complete(deal)`, `returnItem(request, automatic)`. Check the role first, then call these.
- `NotificationService`: `send(user, category, text)` and `sendToAdmins(category, text)` when something happens; `getMyNotifications()`, `markRead(id)` and `getUnreadCount()` for the board (UC4).
- `AuthService`: registration and login (UC1, UC2).

**How a service method is shaped.** One control class per actor and use case (Lecture 16), each method in this order:
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

**Storage.** Data is in memory for now and is lost when the app closes. Saving to files is planned; keep all data access going through `DataStore` so only it has to change.

## How We Build and Test

We use **sandwich integration** (COMP 3111 Lecture 15). The bottom layer (models and `DataStore`) and the top layer (the portal screens from the skeleton) already exist, and we meet in the middle:
- **The shared middle** (`DealRules`, `NotificationService`, the state machines and the conventions above) is built first, bottom-up, and tested with JUnit tests acting as drivers. It holds the critical subsystems: the deal lifecycle, concurrency and the borrowing-deadline timer.
- **Each portal** (buyer, seller, admin) is then built in parallel by its owner, one use case at a time: control class plus tests, then the screen.

| Level | What | Where |
|---|---|---|
| Unit | Equivalence partitions and boundary values (e.g. rating 0/1/5/6, photos 6/7, borrow time 4 s/5 s); state-based tests for each state machine | `src/test/.../models`, `AccountValidatorTest` |
| Integration | Use-case flow tests at service level, including flows across roles (offer → accept → complete → review); run all tests again after every change (regression) | `DealRulesTest`, `DataStoreTest` |
| System / acceptance | One demo scenario per requirement (Tasks 1.x, 2.x, 3.x); teammates test each other's portals | to be written |

Test tools in `src/test/.../testsupport`: `TestClock` (a clock the test moves by hand, so a 5-second borrow is tested instantly) and `Fixtures` (accounts and listings in any state, built through the state machine). Run all tests with `.\mvnw.cmd test`.

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
You need a normal desktop session (not a headless server). On Windows this is usually fine.

### To see the real error
```powershell
.\mvnw.cmd clean javafx:run -e
```
Copy the lines that start with `Exception in thread` or `Caused by`.

## For Students — Next Steps (Backend)

> **⚠️ Outdated for our team:** items 1–3 are done (`DataStore`, PBKDF2 password hashing, `AccessControl`). For item 4, we are saving to files rather than a database.

1. Replace `DummyDataService` with real repositories / REST clients
2. Hash passwords (BCrypt) — never store plaintext
3. Add JWT / session auth and enforce RBAC on the API
4. Persist data in a database
5. Photo upload, chat, auto-return for borrowed items, etc.

## Licence

Educational use — HKUST COMP project skeleton.
