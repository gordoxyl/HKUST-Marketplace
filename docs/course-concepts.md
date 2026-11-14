# COMP 3111 concepts in this project

Where each concept from the course is applied, with one place to look. Lecture numbers are the
course's own (L5 Development, L6-9 Requirements, L10-11 Implementation, L12-14 Testing,
L15-16 Analysis & Design).

## Requirements, analysis and design

| Concept | How it is used here | Where to look |
|---|---|---|
| Use cases with basic and alternative flows (L6-9) | Every feature is built from its use-case specification. A service method covers named steps, and each alternative flow has its own check and message. | `Diagrams and Use-Case Specifications.pdf`; `OfferService` (UC5 A1-A4 named in the code) |
| Domain class diagram (L3, L15) | The model classes follow the diagram: generalizations, constraints such as "at most 6 photos", derived attributes calculated on demand. | `models/`; `DataStore.getTotalViews` |
| State machine diagrams (L15-16) | Listing and Offer change status only through events that refuse invalid transitions. | `docs/state-machines.md`; `Listing.acceptDeal()` |
| Boundary, control and entity classes (L15-16) | Screens are boundaries, one control class per use case holds the rules, entities hold the data. Screens contain no rule. | UC5: `controllers/buyer/MakeOfferFlow` + `OfferDialogs` (boundary), `services/OfferService` (control), `models/Offer` (entity). UC7: `MyDealsView` asks for confirmation, `MyDealsService` decides, and tells the screen which buttons a deal gets (`Deal.canBeMarkedComplete()`) |
| Layers and subsystems; low coupling, high cohesion (L15) | `controllers` / `ui` → `services` → `data` + `models`; a lower layer never uses a higher one. Shared parts (card, chat, notifications, profile) exist once and are used by all three portals. | `README.md`, "Shared Core" |
| Active classes (L15) | The Timer actor of UC12 is an active class with its own thread of control, so deadlines are met whatever screen is open. Its changes are made in one store transaction, so the screens' thread and the timer never see half of each other's work. | `services/BorrowingDeadlineTimer` (active class) calls `BorrowingDeadlineService` (control) |
| Bridge classes for the implementation environment (L15) | `DataStore` talks to a `Storage` interface, so files, format and encryption can change without touching the rest. | `data/Storage`, `EncryptedJsonStorage` |

## Implementation

| Concept | How it is used here | Where to look |
|---|---|---|
| Defensive programming: the barricade (L10) | Everything typed or clicked is untrusted. Services check it and answer with a `ServiceException` written for the user; behind them, model classes take clean values. | `OfferService.submitPriceOffer`, `Amounts.parse` |
| Assertions, preconditions and postconditions (L10) | Conditions that must never be false are checked as assertions or model-level exceptions, never used as input validation. | `OfferService.record` (postconditions); `Listing` state checks |
| Refactoring (L10) | Duplicates are removed when the second use appears. | `services/Amounts` (extracted from the Browse price filter when Make Offer needed it); `ui/ListingCard` |
| Code review (L10) | The shared core was reviewed against the specification; the review found R7. | `docs/regression-log.md`, R7 |

## Testing

| Concept | How it is used here | Where to look |
|---|---|---|
| Unit testing with drivers and stubs (L14) | JUnit classes are the drivers. `TestClock` replaces the real clock, so a 5-second borrow or a reminder 24 hours before a due date is tested without waiting (`BorrowingDeadlineServiceTest`); a store without storage replaces the files. | `src/test/.../testsupport`, `PersistenceTest.CountingStorage` |
| White box: basis path testing and cyclomatic complexity (L12) | Decision-heavy methods get one test per independent path. JaCoCo measures branch coverage, and the build fails below 65%. | `OfferService.checkPeriod` (complexity 5, five tests in `OfferServiceTest`); `target/reports/jacoco` |
| Black box: equivalence partitioning (L13) | Inputs are split into valid and invalid partitions from the specification, and each is tested at least once. | `OfferServiceTest` (price: nothing typed / not a number / not positive / positive), `AmountsTest`, `AccountValidatorTest` |
| Black box: boundary values (L13) | Values at, just below and just above each boundary. | `OfferServiceTest`: price -0.01 / 0 / 0.01; period 4 s / 5 s / 6 s; start now - 1 s / now / now + 1 s. `MyDealsServiceTest`: self-return 1 s before the due date / at it / 1 s after |
| Black box: thread testing (L13) | A test follows one use-case flow through all the objects it touches, often across roles. | `MyDealsServiceTest.oneDeal_fromOfferThroughAcceptanceToCompletion_acrossThreeUseCases`, `OfferServiceTest.offerThenAcceptance_acrossBothRoles_endsWithAReservedListing`, `ChatServiceTest`, `NotificationBoardFlowTest` |
| Black box: state-based testing (L13) | The object is first put into each state through its state machine, then the event is applied and the resulting state checked. | `ListingStateMachineTest`, `OfferServiceTest.listingThatIsNotLive_acceptsNoOffer`, `MyDealsServiceTest` (a deal in each of its five states, with "mark complete" and "return" applied to each) |
| Integration testing: sandwich, critical subsystems (L14) | The shared middle is tested bottom-up with drivers while the portals are built on top. The critical parts (deal lifecycle, concurrency, saving) have their own tests. | `README.md`, "How We Build and Test"; `OfferServiceTest.claimsSubmittedAtTheSameMoment_onlyTheFirstSucceeds` |
| Regression testing (L13-14) | Every bug is reproduced, recorded with its input and correct output, and kept as a test. All tests run on every build. | `docs/regression-log.md` (R1-R8; R8 was reproduced as a failing test before the fix) |
| System and acceptance testing (L14) | Manual scenarios through the screens, one per flow of each use case. | `docs/acceptance-tests.md` |

## Process

| Concept | How it is used here | Where to look |
|---|---|---|
| Scrum sprint, backlog and burn-down (L5) | Sprint 2 has an estimated backlog; remaining hours are recorded daily for the burn-down chart. | Team PDF (Gantt chart, burn-down chart, meeting minutes) |
