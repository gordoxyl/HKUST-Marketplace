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

| Role   | Username  | Password   |
|--------|-----------|------------|
| Admin  | `admin`   | `admin123` |
| Buyer  | `alice`   | `password1`|
| Buyer  | `bob`     | `password1`|
| Seller | `charlie` | `password1`|
| Seller | `diana`   | `password1`|

## Features Implemented (UI Only)

### Shared
- Welcome screen with role selection (Buyer / Seller / Admin)
- HKUST branding (logo + colour palette: deep blue `#003974` + gold `#A68137`)
- Modern card-based UI, icons (Ikonli), consistent sidebars & top bars
  - **⚠️ Outdated for our team:** Ikonli is not in `pom.xml` and is not used by the code.
- In-memory dummy data (no database / no network)

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
    │   ├── controllers/
    │   ├── models/
    │   └── utils/
    └── resources/
        ├── fxml/
        ├── css/styles.css
        └── images/hkust_logo.png
```

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

1. Replace `DummyDataService` with real repositories / REST clients
2. Hash passwords (BCrypt) — never store plaintext
3. Add JWT / session auth and enforce RBAC on the API
4. Persist data in a database
5. Photo upload, chat, auto-return for borrowed items, etc.

## Licence

Educational use — HKUST COMP project skeleton.
