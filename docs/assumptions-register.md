# Assumptions register

This register lists the behaviours the application implements that are stated neither in the course's task list
(`F2026_Project_Tasks_Non_H_Track_Groups.md`) nor in our use-case specification
(`Diagrams and Use-Case Specifications.pdf`). We kept it from 8 Nov 2026 for two reasons: so that the integration,
system and acceptance tests check what was actually decided, and so that the open questions could be put to the
client. The other documents cite it by number ("assumptions register 71"), so the numbers never change. An item
that a later decision replaced says so and keeps its number.

Additions that the specification itself marks as "our addition" are documented there and are not repeated here.
The client's (TA's) answers are in [client-answers.md](client-answers.md); where an answer settles an item, the item
says so.

**Status of each item**

- **Client answer #n**: settled by the client's answer of 16 Nov 2026 ([client-answers.md](client-answers.md)).
- **Edge case, team's choice**: put to the client, who replied on 21 Nov 2026 that it is an edge case not checked
  in the demo, so the team's choice stands.
- **Decided**: decided by the team, with the date. Where the record names who decided, that person is named.
- **Implementation choice**: made while building, where the requirements say nothing.

---

## Deals and listings

1. When the admin removes a **Reserved** listing, the deal already accepted on it can still be completed by the buyer,
   and the listing stays Removed (as for a lent item in UC12 A1). The admin can remove a listing in every status
   except Sold, Given and Removed. **Edge case, team's choice** (client question F2; chosen on 8 Nov 2026).
2. An "available" listing also needs the seller's account to be **active**. The glossary only says: status Live, and
   not a giveaway with a pending claim. Offers on a deactivated seller's listing are refused. **Decided**, 8 Nov 2026.
3. A seller can accept only a **Pending** offer. A Countered offer can only be declined; the buyer agrees to a counter
   by making a new offer with the counter's terms (UC5 SR4). **Decided**, 8 Nov 2026; confirmed by **client answer #2**.
4. The admin can approve or reject only **Pending Review** listings, as implied by Task 3.2, which lists only pending
   listings for review. **Implementation choice.**

## Accounts

5. Usernames are **case-insensitive**, and spaces around them are ignored: "Alice" and "alice" are the same account
   for registration and login (as in Lab 7; the specification says only that usernames are unique). Since 11 Nov 2026
   (regression log R7) the same holds for the seller's username given to a listing, for example by the admin in Add
   Listing: the listing stores it trimmed and in lower case. **Implementation choice.**
6. A user **deactivated while logged in** is refused at their next action, not only at their next login.
   **Implementation choice.**
7. **Blank optional inputs count as not given**: a blank residence hall or seller bio is stored as none. The written
   part of a review is not optional (client answer #8; required since 18 Nov 2026). **Implementation choice**;
   **client answer #8** for the review.
8. A seller's preferred meet-up locations keep the order chosen and drop duplicates. **Implementation choice.**
9. Notifications meant for "the admin" go to **every admin account** (one admin is seeded). **Implementation choice.**

## Notifications

10. Returns use the category **Deal Completed**, because the class diagram's NotificationCategory has no "returned"
    value. The seller is notified of a self-return; the buyer and the seller are both notified of an automatic return.
    **Implementation choice**, in line with **client answer #3** (a lending deal is completed once the item is
    returned).

## Data fields

11. A **report has no date**, because the class diagram has none and no requirement needs one (the team's rule: add
    nothing that the requirements neither state nor imply). **Decided**, 8 Nov 2026.

## Storage (decided on 8 Nov 2026; the requirements say only that data is "securely stored")

12. All data is saved after every change to one **AES-GCM encrypted** JSON file (`marketplace-data/marketplace.dat`),
    never as plain text. A changed or damaged file is detected and refused. **Decided.**
13. The encryption key is **built into the code**, chosen over a separate key file: anyone with the source code can
    decrypt a data file. **Decided.**
14. Deleted-account snapshots are also written as **encrypted** `.txt` files (the diagram says ".txt file") and are
    read through the app. **Decided.**
15. If the saved file cannot be read (damaged, edited, or written with another key), it is moved aside (renamed;
    nothing is deleted), the demo data is used, and the app shows a warning at start-up. **Implementation choice.**
16. During development, runs with shortcut accounts used a separate data file (`marketplace-dev.dat`), so those
    accounts never entered normal data. The release has no development mode and always uses `marketplace.dat`.
    **Implementation choice.**

## Profile (UC3, 9 Nov 2026)

17. Changing one's own profile first sent no notification to the admins, because UC3 requires none. **Replaced on
    24 Nov 2026 by item 88**: every admin is now told of a user's own profile change, as part of the accounts' history
    on the admin's Notification Board (Task 3.6, client question F4).
18. "Leaving the new password blank" (UC3 SR2) means the field is **empty**. A password made only of spaces counts as
    typed and is checked by the strength rules. The confirmation is ignored when no new password is typed.
    **Implementation choice.**
19. A seller picks preferred meet-up locations from the predefined list with **checkboxes** (any number, none
    included). **Implementation choice.**

## Listing card (9 Nov 2026)

20. One shared listing card is used on Browse, My Listings, Listing Review and All Listings. Buyers see the
    **availability** ("Available", "Reserved", "Lent", "Claim Pending", "Seller Deactivated"); the seller and the admin
    see the listing's **status** (the wording of Tasks 2.4 and 3.2). Only the buyer's card is greyed out when the
    listing is not available. **Decided** (one card for every portal); the wording is an **implementation choice**.
21. The price line shows "Borrow" for a lend listing and "Free" for a giveaway; the requirements ask for a price only
    on a sale. **Implementation choice.**
22. The admin's card shows "Submitted: <date time>", which is the listing's posted date (there is no separate
    submission date, and a resubmitted listing keeps its first date), the seller's username next to the full name,
    and the number of photos. **Implementation choice.**
23. A listing with no photo, or whose photo file is missing or unreadable, shows a "No photo" placeholder.
    **Implementation choice.**

## Chat (UC6, 10 Nov 2026)

24. A **Chats page** in the buyer and seller sidebars lists the user's chats. The specification opens a chat only from
    the listing detail, My Deals or an incoming offer. The page is needed because a buyer can start a chat with a
    message and no offer, and the seller would then have no offer to open it from. **Decided**, 10 Nov 2026.
25. Only the buyer starts a chat, and only on a **live** listing (Live, Reserved or Lent, with an active seller). An
    existing chat stays readable and writable for both people, whatever happens to the listing later.
    **Implementation choice.**
26. Spaces and line breaks around a message are dropped; a message of only spaces counts as empty (A1). There is no
    length limit. **Implementation choice.**
27. Chat search ignores case, looks at messages only (offers have no text) and needs a keyword.
    **Implementation choice.**
28. An offer made at the same instant as a message is listed before it. **Implementation choice.**
29. The admin takes no part in chats and cannot read them. The New Message notification reads "<name> sent you a
    message about '<title>'." (no preview), one per message. **Implementation choice.**

## Seller statistics (10 Nov 2026)

30. Total views count all the seller's listings, removed ones too. Offers received count every offer, whatever its
    status. Completed deals are the offers with a completion date. **Implementation choice.**

## Account deletion (10 Nov 2026; the shared `DataStore.deleteAccount`, used by the admin's Delete)

31. Deleting an account deletes the **whole chat threads** the user took part in (the specification says
    "anonymizes/deletes chat history") and the user's notifications. The reviews the user wrote are **kept**: they
    still count in the seller's rating, and their writer is shown as "Deleted User". Reports filed by or about the user
    are kept. **Client answer #6** (archiving, then deleting, is acceptable; reviews by a deleted buyer are kept).
32. In the same atomic step, the deletion declines the user's own open offers and the open offers on the user's
    listings, and sets the user's listings to Removed (except Sold, Given and Removed ones). Deals accepted but not
    completed stay as they are: the automatic return and Mark Complete still work. **Implementation choice.**
33. A deleted account's **username is retired**: it can never be registered again, because the kept deals and
    listings still name it. **Implementation choice.**
34. Notifications to a deleted account are skipped silently. **Implementation choice.**
35. The Audit Log snapshot (stored encrypted, see 14) lists, as they were before the deletion: the profile (without
    the password hash), the deleted chats with their messages, the notifications, the declined offers and the removed
    listings. Since 20 Nov 2026 it has no reviews section, because the reviews are kept. **Implementation choice.**

## Photos (10 Nov 2026)

36. Listing photos are copied into the app's own storage, one **encrypted** file per photo
    (`marketplace-photos/<id>.img`); the listing keeps only their ids. **Decided** ("encrypted like the rest").
37. The accepted formats are PNG, JPEG, GIF and BMP, recognised from the file's content, not its name. There is no
    file-size limit. Only sellers and the admin add photos. **Implementation choice.**
38. A stored photo is never deleted, so a photo of a cancelled or edited listing stays as an unused file.
    **Implementation choice.**

## Browse (UC8, 11 Nov 2026)

39. The keyword search ignores case; a blank keyword matches everything. **Implementation choice.**
40. A typed price must be digits with an optional decimal part ("180", "99.50"). A sign, letters or symbols give the
    invalid-price-range message (A2). A minimum alone or a maximum alone is allowed. **Implementation choice.**
41. Listings with the same price, and the lend listings (which come after the priced ones), are ordered newest first.
    **Implementation choice.**
42. A buyer can open the detail of any live listing. A listing that is no longer live opens only for a buyer who has a
    deal on it, so that My Deals can reopen it. Each opening adds one view; coming back to the detail from the chat or
    the seller's profile does not. **Implementation choice.**
43. The detail screen shows the posted date with its time and the number of views, as part of "all listing fields".
    **Implementation choice.**
44. Reviews are listed newest first, without the reviewer's name (the specification lists rating, comment, date and
    reply). **Implementation choice**; see 72.
45. Browse cards only open the listing (View Details, or a click on the card); Make Offer, Request Borrow and Claim
    Item are on the detail screen (UC8 step 7). Opening Browse from the sidebar starts with no filters; Back from a
    listing keeps them (step 8). **Implementation choice.**

## Make Offer (UC5, 12 Nov 2026)

46. The price form starts with the listing's asking price. The borrow form starts with a period that begins one
    minute from now and lasts the listing's lending duration. The buyer corrects them. When agreeing to a counter,
    the forms start with the counter's terms (as specified). The lending duration is a guide, not a maximum: the
    seller counters or declines a longer request. **Implementation choice**; the lending duration is an **edge case,
    team's choice** (client question F3).
47. A typed price must be digits with at most two decimals, in the Browse price filter too: "150", "99.50". The
    smallest accepted price is 0.01. **Implementation choice.**
48. A borrow period's times are typed as HH:mm or HH:mm:ss, so a 5-second period can be entered. "The start is in the
    past" is judged when the request is submitted, not when the form was opened. A period with both problems reports
    the start first. Since 21 Nov 2026, dates are shown and typed as "21 Nov 2026", in any case. The form reads the
    date the picker shows, so a date typed without pressing Enter still counts, and text in another form
    ("11/25/2026") counts as no date (A3). **Implementation choice**; the date form was **decided** on 21 Nov 2026.
49. A claim is one click with no confirmation question (Task 1.5: "claim the item with one action"; UC5 step 3).
    **Implementation choice.**
50. A buyer can have several open offers on one listing (SR5). A buyer with a pending claim cannot claim the same
    giveaway again, because a giveaway has at most one pending claim. **Implementation choice.**
51. The offer type must fit the listing type. An invalid price is reported before the availability is checked again
    (the order of the flow). **Implementation choice.**
52. The seller's notification (category Offer Received) reads "<buyer> made a price offer of HK$ 200 for '<title>'.",
    "<buyer> sent a borrow request for '<title>'." or "<buyer> claimed '<title>'." **Implementation choice.**
53. The buyer agrees to a counter from the chat (Agree to Counter, see 71) or from My Deals. In a chat, the offer
    button is named after the listing type (Make Offer, Request Borrow or Claim Item) and is always enabled; A1 is
    reported when it is pressed on a listing that is no longer available. **Implementation choice.**

## My Deals (UC7, 12 Nov 2026)

54. Current deals are listed newest first, completed deals most recently completed first. Declined deals are not on
    My Deals (the specification lists only current and completed ones); they stay in the chat. **Implementation
    choice.**
55. A borrowed item can be returned any time after acceptance and strictly before the due date, even before the
    period has started. At the due date itself, self-return is refused and UC12 takes over. **Implementation choice.**
56. The rating prompt after Mark Complete is a yes/no question. Answering no leaves a Rate Seller button on the
    completed deal until it is reviewed (SR6). The same prompt also follows a self-return and an automatic return
    (see 65). **Client answer #3** for the prompt after a return; the wording is an **implementation choice**.
57. A returned item's dates say "Returned" or "Returned automatically" in place of "Completed", and its status badge
    says Returned or Auto-returned (see 63). The due date is shown only while the item is still borrowed.
    **Implementation choice**, allowed by **client answer #1** (returns need not be told apart, but may be).

## Borrowing deadlines (UC12, 13 Nov 2026)

58. A borrow request whose period has already ended cannot be accepted (regression log R8): it is refused with a
    message, and the seller declines or counters. **Decided**, 13 Nov 2026.
59. The timer checks every 0.5 s (SR1 asks for at least once a second), and its first check runs when the app starts,
    so items that became due while the app was closed are reminded and returned at start-up. **Implementation
    choice.**
60. The item is returned when the due date is reached (at the due date, not only after it), matching UC7 SR3, where
    self-return is allowed only before it. **Implementation choice.**
61. An item that reaches its reminder time and its due date in the same check (for example, because the app was
    closed) gets its reminder and then its return, so the postcondition "every buyer whose reminder time was reached
    has received one reminder" holds. **Implementation choice.**
62. The reminder reads "Reminder: '<title>' is due on <due date and time>." and goes to the buyer only. A borrowed item
    returned by the buyer before its reminder time gets no reminder. **Implementation choice.**
63. Open screens reload by themselves when the timer changes data: the buyer's Browse, listing detail, My Deals,
    Notification Board and chats. A returned item's status shows as "Auto-returned" or "Returned".
    **Implementation choice.**
64. Borrow periods, due dates and the due-date reminder show times **to the second** ("14 Nov 2026 14:06:44");
    messages, notifications and postings stay to the minute (regression log R9). **Decided**, 14 Nov 2026.

## Review Seller (UC10, 18 Nov 2026)

65. After an **automatic return**, the rate-seller prompt appears only if the borrower is using the app when it
    happens (the buyer portal is open), and the buyer is asked once per item. An item returned while the buyer was
    away gets no pop-up: its notification ends "You can rate the seller from My Deals.", and the Rate Seller button
    waits. **Decided** (Gordon), 18 Nov 2026.
66. The written review is required (client answer #8) and is any text that is not blank: no minimum or maximum
    length; spaces around it are dropped and line breaks kept. **Client answer #8**; the length rules are an
    **implementation choice**.
67. On submit, the deal is checked again first (A1 ends the use case, whatever was typed), then the rating (A2), then
    the written review. The form starts with no stars chosen; a chosen star can be clicked again to clear it.
    **Implementation choice.**
68. The seller's notification (category Review Received) gives the rating and the whole review: "<buyer> rated you 4
    out of 5 stars for '<title>': "<review>"". **Implementation choice.**
69. Any completed deal can be reviewed, also when its listing was removed later or the seller was deactivated: nothing
    forbids it. **Implementation choice.**
70. After submitting, the buyer's own review is not shown on My Deals (only its Rate Seller button disappears). It
    shows on the seller's profile and listing pages, like every review. **Implementation choice.**

## Chat deal actions (18 Nov 2026; the seller's part changed on 24 Nov 2026)

71. In a chat, offer cards only show the history and the status. Each deal action is a button in one row just under
    Send, shown only while it is that person's turn:
    - **Buyer:** Make Offer, Request Borrow or Claim Item, and Agree to Counter (n). Both are UC5 Make Offer: by
      Special Requirement 4, agreeing makes a new offer with the counter's terms. Agree to Counter lists the countered
      offers, newest first, with the newest chosen, and has Agree on the left and Cancel on the right.
    - **Seller:** Respond in Offers & Requests (n), which is a link, not an answer. The seller answers offers only on
      Offers & Requests (Task 2.5; specification, Manage Offers & Requests 3.3 to 3.5; UC6 has no answering step), so
      the chat does not duplicate that screen. The link opens Offers & Requests with this chat's open offers marked
      in light yellow and the newest scrolled into view; the mark stays until the screen is rebuilt.

    **Decided** (Gordon), 18 Nov 2026; the seller's part on 24 Nov 2026.

## Reviewer names (client answer #6, 20 Nov 2026)

72. Only the screens whose specification steps already show the buyer name the writer of a review: the seller's
    Reviews & Feedback (2.9 step 2, "the buyer's name") and the admin's reviews (6.5, "Buyer"). A deleted buyer is
    shown as "Deleted User". The buyer's listing detail and seller profile (UC8 step 7 and A3) name no writer, because
    the specification lists rating, comment, date and reply only. This is not a privacy rule: the specification's only
    anonymity rule is for reports (UC9 SR4: the seller is never told who reported). **Decided** (Gordon),
    20 Nov 2026, following **client answer #6**.

## Deal History (UC11, Task 1.9; planned and decided on 21 Nov 2026)

73. Deal History lists every completed deal of the logged-in buyer, whatever the listing's status now and whatever the
    seller's account state. **Implementation choice.**
74. Date range: either end is optional, both ends are included, and dates are compared by calendar day. A2 only when
    both ends are set and the start is after the end; a start equal to the end is valid. **Implementation choice.**
75. Rows show the listing's **current** title and category, and the category filter uses them. No copy is kept at
    completion (the class diagram has none), so a later edit by the admin or the seller changes past rows.
    **Decided** (Gordon), 21 Nov 2026.
76. The seller search matches the displayed seller name (the full name; the username for a deleted account),
    ignoring case and spaces around it; a blank search means no search. **Implementation choice.**
77. Newest deal date first; for the same date, the newest offer first. The columns cannot be re-sorted.
    **Implementation choice.**
78. Choosing a date or a category searches at once; the seller's name is searched with Enter or Search; Reset clears
    every filter. The dates are chosen from the calendar, not typed, and are shown as the table shows them
    ("21 Nov 2026"). An invalid range (A2) is shown inline, as red text under the dates, not as a dialog: the earlier
    results stay, and the message stays through a timer refresh until the range is valid. **Decided** (Gordon),
    21 Nov 2026, for the inline A2 and the date form, because a dialog at every date change would break the usability
    requirement. The rest is an **implementation choice**.
79. One A1 message serves an empty history and a search with no match: "No deals match. A deal appears here once it
    is completed." The Outcome keeps "Auto-returned" for a timer return (UC11 SR4), although client answer #1 makes it
    optional. **Decided** (Gordon), 21 Nov 2026.

Also decided on 21 Nov 2026, with no item of their own: there is no title search (UC11 step 3 searches by seller
only); the filters combine, so a deal must match every filter that is set (UC11 step 4, over Task 1.9's "or"); and
completed deals also stay on My Deals (UC7 step 2).

## Report Listing (UC9, Task 1.4; planned and decided on 21 Nov 2026, built on 22 Nov 2026)

80. A listing can be reported whenever the buyer can open its detail screen: any live listing, and any listing the
    buyer has a deal on, whatever its status (Sold, Given and Removed included). There is no extra rule. **Decided**
    (Gordon), 21 Nov 2026.
81. {Check Report} is repeated at submit, in the same transaction as the recording. A listing reported in the
    meantime gives A1 (SR3 under a double submit). A listing that can no longer be opened gives "This listing is no
    longer on the marketplace." and the use case ends: an outcome that UC9 does not list but its precondition implies.
    **Decided** (Gordon), 21 Nov 2026.
82. Details made only of spaces or line breaks are empty (A2). There is no minimum or maximum length; the ends are
    trimmed and line breaks kept, as for written reviews (item 66). **Implementation choice.**
83. Every admin account is notified (category Listing Reported): "'<title>' by <seller name> was reported (<reason>)
    and needs moderation." The reporter and the details are not in it; the admin sees them on the listing's details.
    **Decided** (Gordon), 21 Nov 2026, that every admin is notified; the wording is an **implementation choice**.
84. The form starts with no reason chosen, with the reasons in SR1's order. After A2 it comes back as entered.
    Cancel says nothing. **Implementation choice.**
85. The Report Listing button stays enabled after a report; pressing it again gives A1. **Implementation choice.**
86. The data store refuses a report on an unknown listing, or by an account that is not a buyer (regression log R10).
    **Decided** (Gordon), 21 Nov 2026.
87. "Reported" in the admin's status filter (Manage Marketplace Listings step 2) is not a listing status: the Listing
    state machine has no such state, and client answer #7 gives reports no status, so it could never be cleared. It is
    a derived, permanent filter: "has at least one report". **Decided** (Gordon), 21 Nov 2026.

## The admin's Notification Board (Task 3.6, client question F4; 24 Nov 2026)

88. The admin's Notification Board is the **accounts' history** (Task 3.6: "user account changes / updates"). Every
    change to a buyer's or seller's account goes to every admin as an Account Change, the acting admin included,
    because it is a record. The events are: a new account (registration); a user's own profile change, naming the
    fields that changed but never their values (an admin's own profile is not included); and the admin's edit,
    deactivate, reactivate and delete, with the reason where one is given, for example "@ada deleted the buyer account
    of Bea Chan (@bea). Reason: Spam". **Decided** (Gordon), 24 Nov 2026; client question F4 left it to the team.

## UI changes of 25 Nov 2026 (no data added)

89. **Back to Chats.** A chat chosen on the Chats page has "← Back to Chats" above it, which closes the chat and shows
    the Chats page as it first opens. A chat opened from another screen keeps Back to that screen (Back to listing,
    Back to My Deals, Back to Offers & Requests). **Decided** (Gordon), 25 Nov 2026.
90. **The chat screen is a list beside the chat.** Every chat opens in one chat screen, the same for buyers and
    sellers: the user's chats in a list (newest message or offer first, the open chat marked in light blue), the chat
    beside it with a light grey line between them, and a button "◀ Hide chats" / "▶ Show chats" that hides and shows
    the list while a chat is open. Sending a message, or making an offer from the chat, moves the chat to the top of
    the list. **Decided** (Gordon), 25 Nov 2026.
91. **Mark all as read** on the Notification Board is shown only while something is unread. It marks every unread
    notification of the logged-in user in one transaction, with no confirmation (like Mark as read), and cannot be
    undone, as the specification has no "mark as unread". It extends UC4 step 3 to all notifications at once; UC4 SR3
    holds, since the user marks them. Opening the board never marks anything. **Decided** (Gordon), 25 Nov 2026.
92. **Unread dot** on the sidebar's Notifications button, with the number of unread notifications ("1" to "99", then
    "99+"; hidden at 0; tooltip "n unread notifications"). Only marking notifications as read lowers it (SR3). It is
    updated at login, on every page change, by the board after marking, and at once when the timer changes data. It
    is in the buyer and seller portals; the admin portal adds it as described in
    [shared-core-for-admin.md](shared-core-for-admin.md). **Decided** (Gordon), 25 Nov 2026.
93. **Category filter** on the Notification Board: "All categories", plus the categories that the user's own
    notifications have, in the order of NotificationCategory (a category the user never receives is not offered). It
    only changes what is shown; the chosen category stays when the board reloads, and the heading's unread count stays
    the whole board's. **Decided** (Gordon), 25 Nov 2026.

With these changes, the admin's View listing shows the listing with the same overview as the buyer's detail screen
(photos, fields, description).

## The admin's Edit listing (Task 3.7; 26 Nov 2026)

94. **The admin's Edit listing changes the item's condition too.** The specification (Manage Marketplace Listings
    5.4.2) lists the fields the admin modifies as title, description, category, price, location and photos. But Task
    3.7 says "Edit any listing details", and the seller's own Edit listing can change the condition. So the admin's
    form shows the listing's current condition and saves the one chosen, and the condition is required, as when
    adding a listing ("The item condition is required."). The admin's Edit also changes a lend listing's lending
    duration (left empty, it keeps the current one; regression log R14), which 5.4.2 does not list either.
    **Decided** (Gordon), 26 Nov 2026.

---

## Known limitations

- **L1. Not a Java module.** The project has no `module-info.java`, so JavaFX is loaded from the class path and
  prints a harmless warning at start-up and in test runs: "Unsupported JavaFX configuration: classes were loaded from
  'unnamed module'". Behaviour is not affected. Making it a module needs `requires` and `opens` lines for FXML (the
  controllers) and Jackson (the models and the data), module-aware tests, and changes to the start-up test's child
  JVM, the scripts and `javafx:run`. A side effect today: running `MarketplaceApp.java` directly (right-click, Run)
  fails with "JavaFX runtime components are missing", so the app is run through Maven (README, Quick start).
  Deferred: the change touches the build, every test run and the scripts at once.

## Test data the tests rely on

These are not requirements; the demo data and the tests depend on them.

- Demo accounts: buyers `alice` and `bob`, sellers `charlie` and `diana`, all with the password `Demo@1234`; the
  admin `admin`, password `Admin@1234`.
- Seeded states: *Calculus Textbook (Stewart)* Reserved by Alice's accepted HK$ 160 offer; *Organic Chemistry
  Textbook* sold to Bob, with his 5-star review of Charlie; *Mini Fridge* Pending Review; *Graphing Calculator
  TI-84* Live, with Bob's pending borrow request.
