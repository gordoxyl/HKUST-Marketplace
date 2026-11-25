# Acceptance test scenarios

System and acceptance tests are black-box tests of the whole application through its screens
(COMP 3111 Lecture 14). Each scenario below comes from a use case in
`Diagrams and Use-Case Specifications.pdf`: one for the basic flow, one for each alternative flow,
and one for each special requirement that a user can see. They complement the automated tests:
the unit and integration tests stop at the service layer, and the screen tests
(`controllers/buyer/*ScreensTest`) run the buyer's flows through the screens. This page is the
manual checklist for the demo, and for what an automated test cannot judge, such as how a screen looks.

**How to run them**

1. Close the app and reset the demo data: `scripts\reset-data.cmd` (Windows) or `sh scripts/reset-data.sh` (macOS).
2. Start the app: `.\mvnw.cmd clean javafx:run`.
3. Follow the steps, compare with the expected result, and fill in the Result column (Pass / Fail, date, tester).
   A Fail goes into `docs/regression-log.md` with the steps that reproduce it.

Demo accounts: buyers `alice`, `bob`; sellers `charlie`, `diana` (password `Demo@1234`); admin `admin` (`Admin@1234`).
Demo listings after a reset: *MacBook Pro Charger 61W* (sale, HK$ 250, Charlie, available), *IKEA Desk Lamp*
(giveaway, Diana, available), *Graphing Calculator TI-84* (lend, 90 days, Diana, available; Bob has a pending
borrow request), *Calculus Textbook (Stewart)* (sale, HK$ 180, Charlie, **Reserved** for Alice).

Scenarios marked *needs Task 2.5* need a seller to accept or counter an offer: log in as the seller
(`charlie` or `diana`) and use **Offers & Requests**. To skip those steps, run `scripts\practice-data.cmd`
after the reset: it adds deals that a seller has already accepted or countered for `alice` (Desk Fan, Free
Notebooks, Camera Tripod, HDMI Cable, Bluetooth Speaker, Camping Tent), and the scenarios can be run on them.

Scenarios for the other use cases are added as each one is built.

---

## UC8 Browse Marketplace (Tasks 1.3, 1.4)

Log in as `alice` for every scenario.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC8-01 | Basic flow, steps 1-2 | Open Browse Marketplace. | 4 listings, newest first: calculator, lamp, charger, calculus textbook. Each card shows photo (or "No photo"), title, seller, category, type, price, condition, meet-up location, posted date and availability. The calculus textbook is greyed out and says "Reserved". | |
| AT-UC8-02 | Steps 3-4, keyword | Type `textbook`, press Search. Then type `CHARGER`. | Only the calculus textbook; then only the charger (case is ignored). | |
| AT-UC8-03 | Steps 3-4, filters | Reset. Choose category Electronics. Then also type Lend. | Calculator and charger; then only the calculator. | |
| AT-UC8-04 | Steps 3-4, price range and sort (SR4) | Reset. Type `200` as the maximum price and choose "Price: low to high". | Lamp (counts as HK$ 0), then calculus textbook (HK$ 180). No charger (HK$ 250) and no calculator (a lend listing has no price). | |
| AT-UC8-05 | Sort by price (SR4) | Reset. Choose "Price: high to low". | Charger, calculus textbook, lamp, then the calculator last. | |
| AT-UC8-06 | A1 No listings found | Type `unicorn`, press Search. | "No listings match. Try another keyword or fewer filters." | |
| AT-UC8-07 | A2 Invalid price range | Reset. Type minimum `200`, maximum `100`, press Search. Repeat with minimum `-5`, and with `abc`. | An error each time: "The price range is invalid…". The listings shown before stay. | |
| AT-UC8-08 | Steps 5-7, SR2 view count | Open the charger with View Details. Note "Views". Go back and open it again. | The detail shows the photos (or "No photo"), every field, the description, Seller: Charlie Lee with "Seller rating: 5.0 / 5 (1 rating)" and Bob's review, and the buttons Make Offer, Chat with Seller, Report Listing. Views goes up by exactly one per opening. | |
| AT-UC8-09 | Step 8 | Search `charger`, open it, press "Back to listings". | The keyword `charger` and the one result are still there. | |
| AT-UC8-10 | SR1 unavailable listing | Reset the search. Open the calculus textbook. | The detail opens. Availability "Reserved". Make Offer is disabled; Chat with Seller works. | |
| AT-UC8-11 | A3 Seller profile | On the charger's detail, click the seller's name. Press "Back to listing". | Profile: Charlie Lee, @charlie, his bio, "Seller rating: 5.0 / 5 (1 rating)", Bob's review with stars, date and comment. Back shows the listing again, and Views has not gone up. | |
| AT-UC8-12 | SR5 hidden review | Log in as `admin`, Seller Ratings, hide the review of charlie. Log in as `alice`, open the charger. | "Seller rating: No ratings yet" and no review, on the detail and on the profile. | |

## UC5 Make Offer (Task 1.5)

Log in as `alice` unless a step says otherwise.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC5-01 | Basic flow, price offer | Open the charger, press Make Offer. Change the price to `200`, press Submit. Then open Chats. Then log in as `charlie` and open Notifications. | The form starts with `250`. Confirmation: "Your price offer for 'MacBook Pro Charger 61W' was submitted: HK$ 200. The seller has been notified." The charger is still Available. Alice's chat with Charlie shows "Price offer: HK$ 200" as Pending. Charlie has an Offer Received notification: "Alice Chan made a price offer of HK$ 200 for 'MacBook Pro Charger 61W'." | |
| AT-UC5-02 | Basic flow, borrow request | Open the calculator, press Request Borrow, press Submit without changes. Log in as `diana`, open Notifications. | The form suggests a start one minute from now and an end 90 days later. Confirmation names the period. Diana has "Alice Chan sent a borrow request for 'Graphing Calculator TI-84'." | |
| AT-UC5-03 | Basic flow, claim (step 3; SR1) | Open the lamp, press Claim Item. Then log in as `bob` and open Browse. | No form: the confirmation appears at once. The detail now says "Claim Pending" and Claim Item is disabled. For Bob the lamp's card is greyed out with "Claim Pending", and on its detail screen Claim Item is disabled. | |
| AT-UC5-04 | A1 Listing not available | Open Chats, open the chat about the calculus textbook (Reserved), press Make Offer. | "This listing is no longer available." No form appears, and nothing is added to the chat. | |
| AT-UC5-05 | A2 Invalid price | Open the charger, press Make Offer. Submit an empty price, then `abc`, then `0`, then `-5`. Then press Cancel. | Each time: "The price must be a positive amount, such as 150 or 99.50.", and the form comes back with what was typed. After Cancel nothing is recorded: no new offer in the chat, no notification for Charlie. | |
| AT-UC5-06 | A2 boundary | Make Offer on the charger with `0.01`. | Accepted: "…was submitted: HK$ 0.01." | |
| AT-UC5-07 | A3 Invalid borrow period | Open the calculator, press Request Borrow. (a) Clear the end time, Submit. (b) Set the start date to yesterday, Submit. (c) Set the end to 4 seconds after the start, Submit. | (a) "Please enter both the start and the end of the borrow period, each with a date and a time." (b) "The borrow period cannot start in the past." (c) "The borrow period must end at least 5 seconds after it starts." The form comes back each time with what was entered. | |
| AT-UC5-08 | A3 boundary (SR3) | In the same form, set the end to exactly 5 seconds after the start (for example 14:30:00 and 14:30:05), Submit. | Accepted. | |
| AT-UC5-09 | A4 Cancel | Press Make Offer on the charger, then Cancel. | No message; nothing is recorded. | |
| AT-UC5-10 | Step 1, Make Offer from the chat | After AT-UC5-01: open Chats, open the charger chat, press Make Offer, submit `210`. | The new offer appears in the chat below the first one. | |
| AT-UC5-11 | SR5 earlier offers stay | Continue from AT-UC5-10. | Both offers (HK$ 200 and HK$ 210) are listed as Pending. | |
| AT-UC5-12 | SR4 agreeing to a counter | Needs the seller's Offers & Requests (Task 2.5): as `charlie`, counter Alice's HK$ 200 offer with HK$ 230. As `alice`, open the chat, press **Agree to Counter (1)** under Send, choose the offer ("HK$ 200 → HK$ 230") and press Agree, then Submit. | The form starts with `230`. A new Pending offer of HK$ 230 appears; the first offer stays "Countered". | |
| AT-UC5-13 | Saved data | After any of the scenarios above, close the app and start it again. | The offers are still in the chats, and the lamp is still "Claim Pending". | |
| AT-UC5-14 | A3, dates in the borrow form | Open the calculator, press Request Borrow. (a) Look at the dates. (b) Type the start date as `25 Nov 2026`, without pressing Enter, and press Submit. (c) In a new request, type the start date as `11/25/2026` and press Submit. | (a) The dates read like every other date in the app, such as "21 Nov 2026". (b) The request starts on 25 Nov 2026: a typed date counts even without Enter. (c) "Please enter both the start and the end of the borrow period, each with a date and a time." The form comes back with the start date empty and the hint "such as 21 Nov 2026". | |


## Chat deal actions (UC5, UC6 and Manage Offers & Requests; assumptions register 71)

The offer cards in a chat only show history and status. The deal actions are buttons under Send, and each appears
only while it is that person's turn. The buyer makes offers there (UC5). The seller answers offers only on Offers &
Requests (Task 2.5), which the chat links to with the chat's offers marked. The seller portal gives every chat
that link (`ChatView.setOnRespondToOffers`, PR #21).
The rules behind them are tested in `OfferActionTest`, `ConversationTest`, `ChatTextTest` and `ScrollingTest`.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-CHAT-01 | Buyer: actions under Send | As `alice`, open the chat of a listing without countered offers. | Under Send: only the listing's action (Make Offer, Request Borrow or Claim Item). No offer card has a button. | |
| AT-CHAT-02 | Buyer: Agree to Counter, from the row *(needs a countered offer: counter one as the seller, or use the practice deals)* | After `scripts\practice-data.cmd`, open the chat of the Bluetooth Speaker. | Under Send: Make Offer and **Agree to Counter (1)**. The countered card shows "Seller's counter: …" and has no button. | |
| AT-CHAT-03 | Buyer: the Agree dialog | Press Agree to Counter (1). | A dialog "Agree to a counter from Charlie Lee" lists "HK$ 220 → HK$ 260 · <time>" already chosen, with Agree on the left and Cancel on the right. | |
| AT-CHAT-04 | Buyer: cancelling goes back to the list | Press Agree, then Cancel in the Make Offer form. | The Agree dialog opens again. Cancel closes it; nothing was submitted. | |
| AT-CHAT-05 | Buyer: agreeing | Press Agree, then Submit in the form (it starts with 260). | "Your price offer … was submitted: HK$ 260." A new Pending HK$ 260 card appears; the countered one stays Countered, so Agree to Counter (1) remains. | |
| AT-CHAT-06 | Seller: the link under Send | As `charlie`, open the chat of an offer that is waiting (from Chats, or Offers & Requests → Open Chat). | Under Send: **Respond in Offers & Requests (n)**, n being the chat's open offers (Pending and Countered). No Accept, Decline or Counter anywhere in the chat; no offer card has a button. | |
| AT-CHAT-07 | Seller: the link opens the offers | Press Respond in Offers & Requests (n). | Offers & Requests opens, with its sidebar button active. The listing's card is in view, and the chat's open offers are marked light yellow, the same yellow as a chat search result. Offers from other chats are not marked. | |
| AT-CHAT-08 | Seller: the mark stays until the screen changes | Wait a few seconds. Then open My Listings, and come back to Offers & Requests from the sidebar. | The mark stays while the screen is open. Coming back from the sidebar, nothing is marked. | |
| AT-CHAT-09 | Seller: answering, then back to the chat | From AT-CHAT-07, Accept the marked offer and confirm. Then open the chat again from Chats. | "Offer accepted"; the list is shown again, without marks. In the chat, the card says Accepted, the listing's other open offers say Declined, and the link is gone. | |
| AT-CHAT-10 | Seller: two open offers from one buyer | After AT-CHAT-05 (Alice agreed to Charlie's counter, so her countered offer and her new offer are both open), as `charlie` open that chat and press Respond in Offers & Requests (2). | Both of Alice's offers are marked, and the newest is in view. The Countered one has only Decline enabled; the Pending one has Accept, Decline and Counter. | |
| AT-CHAT-11 | Seller: loans and giveaways | As `diana`, open Bob's borrow request on the calculator, then a claim on the desk lamp, and press the link in each. | The same label in both, Respond in Offers & Requests (1). The request is marked and has Counter, which asks for dates; the claim is marked and has no Counter. | |

## The chat screen (UC6; assumptions registers 89 and 90)

Every chat opens in one chat screen, the same for buyers and sellers: the user's chats in a list on the left, newest
first, and the open chat beside it, marked in the list. A button hides the list, so the chat takes the whole width,
and shows it again. Above the chat is Back: **Back to Chats** when the chat was chosen on the Chats page, otherwise
back to the screen it was opened from. Run in order after a reset. The automated checks are in `ChatScreensTest`
and `ChatServiceTest`.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-CHAT-12 | The Chats page | As `alice`, open Chats. | Her chats in a list on the left, the newest first. Beside it: "Choose a chat to read and write its messages." The page title is Chats; there is no Back and no Hide button. | |
| AT-CHAT-13 | Opening a chat | Press Open Chat on the Calculus Textbook (Stewart) chat, or click the chat. | The chat opens beside the list, with a light grey line between them, and its card in the list turns light blue with a blue edge. Send, Search and the button under Send read in full; a long title takes a second line. The title is "Chat: Calculus Textbook (Stewart)". Above: **← Back to Chats** and **◀ Hide chats**. | |
| AT-CHAT-14 | Hiding and showing the list | Press ◀ Hide chats, then ▶ Show chats. | Hidden, the chat takes the whole width, the grey line goes with the list, and the button reads "▶ Show chats". Shown again, the list is as it was. | |
| AT-CHAT-15 | Another chat | Start a second chat: Browse → IKEA Desk Lamp → Chat with Seller → write "Hi" and Send. Open Chats, open the Calculus chat, then click the IKEA Desk Lamp chat in the list. | It opens beside the list, the light-blue mark moves to it, and the title changes. Back still reads Back to Chats. | |
| AT-CHAT-16 | Back to Chats | Press ← Back to Chats. | The Chats page as in AT-CHAT-12: the list alone, nothing marked, the title Chats. | |
| AT-CHAT-17 | Writing moves the chat up | Open the Calculus chat (now second in the list), write "Is it still available?" and press Send. | The message appears in the chat, and the chat moves to the top of the list with the message as its preview. | |
| AT-CHAT-18 | From a listing, from a deal | Browse → IKEA Desk Lamp → Chat with Seller, then press Back. Then My Deals → Open Chat on the Calculus Textbook, then press Back. | Each time the same chat screen, with the list beside the chat. Back reads **← Back to listing** and returns to the listing, then **← Back to My Deals** and returns to My Deals; never Back to Chats. | |
| AT-CHAT-19 | Seller: the same screen | As `charlie`, open Chats and open Alice's chat about the Calculus Textbook. Then open Offers & Requests and press Open Chat on any offer. | The same screen as in AT-CHAT-12 to 16; while an offer is waiting, the chat has the link of AT-CHAT-06 under Send. Open Chat opens the same screen, with the list beside the chat and "← Back to Offers & Requests" above; Back returns to Offers & Requests. | |

## UC7 Manage My Deals (Task 1.6)

After a reset, Alice has one accepted deal (HK$ 160 for the calculus textbook), and Bob has a pending
borrow request (calculator) and a completed, already reviewed purchase (organic chemistry textbook).
Scenarios marked *needs Task 2.5* need the seller's Manage Offers screen to accept or counter an offer.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC7-01 | Basic flow, steps 1-2 | Log in as `alice`, open My Deals. | "Current deals" has Calculus Textbook (Stewart): Seller: Charlie Lee · Price offer, status Accepted, "Price offer: HK$ 160", the dates it was made and accepted, and the buttons Mark Complete, View Listing, Open Chat. | |
| AT-UC7-02 | Step 2, current then completed | Log in as `bob`, open My Deals. | "Current deals": the calculator, a Borrow request with its period, status Pending, no Mark Complete or Return Item. "Completed deals": Organic Chemistry Textbook, status Completed, with no Rate Seller button (Bob already reviewed it). | |
| AT-UC7-03 | A3 Not confirmed | As `alice`, press Mark Complete, then Cancel. | The deal is still Accepted under Current deals. | |
| AT-UC7-04 | Basic flow, steps 3-8 | Press Mark Complete, then OK. Answer the rating question with Cancel. Then log in as `charlie`: open My Listings and Notifications. | The deal moves to "Completed deals" with status Completed and today's date. Charlie's Calculus Textbook is Sold, and he has a Deal Completed notification: "Alice Chan marked the deal for 'Calculus Textbook (Stewart)' complete." | |
| AT-UC7-05 | Step 9, rating prompt | Continue from AT-UC7-04 as `alice`. | Right after completing, the question "…Would you like to rate and review the seller now?" appeared. The completed deal has a Rate Seller button. (Rating itself is UC10.) | |
| AT-UC7-06 | A4 Reopen listing (UC8 SR2) | On the completed calculus deal press View Listing, note Views, press "Back to My Deals", and open it again. | The detail opens although the listing is Sold, with Make Offer disabled. Views goes up by one per opening. Back returns to My Deals. | |
| AT-UC7-07 | A4 Reopen chat | Press Open Chat on the calculus deal, then "Back to My Deals". | The chat with Charlie Lee shows the offer and both messages. Back returns to My Deals. | |
| AT-UC7-08 | A1 No deals | Register a new buyer, log in, open My Deals. | "You have no deals yet. Open a listing in Browse Marketplace to make an offer, send a borrow request or claim an item." | |
| AT-UC7-09 | New deals appear (UC5) | As `alice`, make an offer on the charger and claim the lamp (AT-UC5-01, AT-UC5-03), then open My Deals. | Both are under Current deals as Pending, the newest first, above older ones. | |
| AT-UC7-10 | A2 Return borrowed item *(needs Task 2.5)* | As `diana`, accept Bob's borrow request. As `bob`, open My Deals, press Return Item, then OK. | Before: status Accepted, "Due" with the end of the period, and a Return Item button. After: "'Graphing Calculator TI-84' was returned. Diana Ng has been notified."; the deal is under Completed deals with "Returned" and today's date; Diana has "Bob Wong returned 'Graphing Calculator TI-84'."; the calculator is Available again on Browse. | |
| AT-UC7-11 | SR3 no self-return after the due date *(needs Task 2.5)* | As `alice`, request the calculator for a 5-second period; as `diana`, accept it; wait until the end has passed; as `alice`, open My Deals. | No Return Item button: the item is returned automatically (UC12). | |
| AT-UC7-12 | SR4 Agree to a counter *(needs Task 2.5)* | As `charlie`, counter Alice's offer on the charger. As `alice`, open My Deals and press Agree to Counter, then Submit. | The deal shows "Seller's counter: HK$ …" and an Agree to Counter button. The form starts with the counter price. Afterwards a new Pending offer is listed above the Countered one. | |
| AT-UC7-13 | Saved data | After AT-UC7-04, close the app and start it again. | The calculus deal is still under Completed deals, and the listing is still Sold. | |

## UC12 Process Borrowing Deadlines (Tasks 1.6, 1.8)

The Timer starts with the app and checks twice a second. These scenarios need a borrowed item:
run `scripts\practice-data.cmd` after a reset, then **start the app within a minute** and log in as
`alice`. The practice *HDMI Cable* is borrowed for 5 minutes from when the script ran: the reminder
comes after 2.5 minutes, the automatic return after 5. Borrow times are shown to the second (My Deals:
"Due … 14:06:44"); the return should come within a second of that time (Special Requirement 1).

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC12-01 | Step 2, reminder (SR2: halfway for a short period) | Open the Notification Board and wait until 2.5 minutes after the script ran. | A "Due-Date Reminder" appears by itself, without reopening the board: "Reminder: 'HDMI Cable' is due on <date and time>." | |
| AT-UC12-02 | SR3, one reminder | Keep the board open for another minute; open it again. | Still exactly one reminder for the HDMI Cable. | |
| AT-UC12-03 | Step 3-4, automatic return | Open My Deals and wait until 5 minutes after the script ran. | Without clicking, the HDMI Cable moves to "Completed deals" with status **Auto-returned** and "Returned automatically <date>"; its Return Item button is gone and Rate Seller appears. The rate-seller prompt pops up too (AT-UC10-08); press Cancel to keep the review for later. | |
| AT-UC12-04 | Step 3, notifications (SR6) | Open the Notification Board. Then log in as `charlie` and open Notifications. | Alice: "'HDMI Cable' was returned automatically because the borrowing period ended. You can rate the seller from My Deals." Charlie: "'HDMI Cable', borrowed by Alice Chan, was returned automatically…". | |
| AT-UC12-05 | Step 3, listing Live again | As `alice`, open Browse. | The HDMI Cable is no longer greyed out: Available, and Request Borrow works on its detail. | |
| AT-UC12-06 | App closed past the due date | Run the practice script, keep the app closed for more than 5 minutes, then start it and open My Deals. | The HDMI Cable is already Auto-returned, and there is one reminder and one return notification. | |
| AT-UC12-07 | A1 listing removed while lent *(needs Task 3.7)* | While the cable is lent, as `admin` remove the listing; wait for the due date. | The deal is Auto-returned with both notifications, but the listing stays Removed and is not on Browse. | |
| AT-UC12-08 | SR5 returned once | Press Return Item on the HDMI Cable a few seconds before it is due, confirm after the due date has passed. | Either it was returned by Alice, or the message "The borrowing period has ended. The item is returned automatically." appears; never both, and only one return notification. | |
| AT-UC12-09 | Late acceptance refused (R8) *(needs Task 2.5)* | As `alice`, request the calculator for a 5-second period; wait 10 seconds; as `diana`, accept it. | "The borrow period of this request has already ended. Decline it, or counter with new dates." The request stays Pending. | |

## UC10 Review Seller (Task 1.10)

The client's answers (`docs/client-answers.md`) change two things: the written review is required (#8),
and the rate-seller prompt also follows the return of a borrowed item (#3). A1 (the deal cannot be
reviewed) cannot be reached through the screens, because only a completed deal without a review has a
Rate Seller button; it is covered by `ReviewServiceTest`.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC10-01 | Basic flow, from the prompt after Mark Complete (UC7 step 9) | As `alice`, open My Deals, press Mark Complete on the Calculus Textbook and confirm. Answer OK to the prompt. Choose 4 ★, write "Smooth deal, met on time.", press Submit Review. | The form is headed "Rate and review Charlie Lee for 'Calculus Textbook (Stewart)'". After submitting: "Thank you. Your review of Charlie Lee was submitted…". On My Deals the deal has no Rate Seller button. | |
| AT-UC10-02 | Step 8, shown to all users | Open Browse, a listing of Charlie's, then Charlie's profile. | The review (★★★★☆, the comment, today's date) is listed on both, and the average rating and the number of ratings include it. | |
| AT-UC10-03 | Step 9, the seller is notified | Log in as `charlie` and open Notifications. | "Review Received": "Alice Chan rated you 4 out of 5 stars for 'Calculus Textbook (Stewart)': "Smooth deal, met on time."" | |
| AT-UC10-04 | A2, no rating | Open a review form, write a review, and press Submit Review without choosing stars. | "A rating from 1 to 5 stars is required." The form comes back with the review still written. | |
| AT-UC10-05 | Written review required (client answer #8) | Choose stars, leave the review empty or only spaces, press Submit Review. | "A written review is required. Tell other buyers how the deal went." The form comes back with the stars still chosen. | |
| AT-UC10-06 | A3, skip | In the form, press Skip. | No message, and nothing is recorded. The deal keeps its Rate Seller button, which opens the form again. | |
| AT-UC10-07 | Prompt after the buyer returns an item (client answer #3) | After `scripts\practice-data.cmd`, as `alice`, press Return Item on the Camera Tripod and confirm. | "'Camera Tripod' was returned, and Diana Ng has been notified. Would you like to rate and review the seller now?" OK opens the form. | |
| AT-UC10-08 | Prompt after an automatic return, in the app (client answer #3) | As in AT-UC12-03, stay logged in as `alice` until the HDMI Cable is returned automatically. | Without clicking: "'HDMI Cable' was returned automatically because the borrowing period ended. Would you like to rate and review Charlie Lee now?" OK opens the form; Cancel leaves the Rate Seller button. | |
| AT-UC10-09 | Automatic return while away (client answer #3) | As in AT-UC12-06, keep the app closed past the due date, then log in as `alice`. | No pop-up. The notification ends "You can rate the seller from My Deals.", and the deal has a Rate Seller button. | |
| AT-UC10-10 | SR1 once per deal; two loans give two reviews (client answer #8) *(needs Task 2.5)* | Borrow the calculator, have `diana` accept, return it and review it; then borrow it again and return it. | The second loan has its own Rate Seller button and review; each deal is reviewed once. | |
| AT-UC10-11 | SR3, no change after submitting | After submitting a review, look for a way to change or delete it. | There is none; the deal has no Rate Seller button any more. | |

## UC11 View Deal History (Task 1.9)

The client's answers fix two columns (`docs/client-answers.md`): the Deal Date is the completion date (#4), and an
automatic return may be shown as Auto-returned (#1). A borrowed item is dated on its return day, and an automatic return
on its due date. Filters combine: a deal is shown only if it matches every filter that is set (step 4). Scenarios 02-12
use the practice data: `alice` already has the USB-C Hub (Diana Ng, bought yesterday) and the Umbrella (Charlie Lee,
returned automatically two days ago).

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC11-01 | Basic flow, steps 1-2 | After a reset, as `bob`, open Deal History. | One row: Organic Chemistry Textbook · Charlie Lee · the date 18 days before the reset · Bought · Completed. The line above the table says "1 deal". | |
| AT-UC11-02 | Step 2, SR2-SR4 | After `scripts\practice-data.cmd`, as `alice`, open Deal History. | Two rows, newest first: USB-C Hub · Diana Ng · yesterday · Bought · Completed; Umbrella · Charlie Lee · two days ago · Borrowed · Auto-returned. | |
| AT-UC11-03 | SR1, a deal enters at once | On My Deals, mark the Desk Fan and then the Free Notebooks complete, and return the Camera Tripod. Open Deal History. | Three new rows at the top, dated today, newest first: Camera Tripod (Borrowed, Returned), Free Notebooks (Received, Completed), Desk Fan (Bought, Completed). | |
| AT-UC11-04 | SR1, the timer while the screen is open | Close the app, reload the practice data and start the app. Within 5 minutes, as `alice`, open Deal History and wait without clicking until the HDMI Cable is due. | Within a second of its due time the HDMI Cable appears at the top: Charlie Lee · today · Borrowed · Auto-returned. | |
| AT-UC11-05 | Step 4, date range | Choose yesterday's date as both From and To. | Only the USB-C Hub (a one-day range includes its day). | |
| AT-UC11-06 | Step 4, category | Press Reset, then choose Others. | Only the Umbrella. Choosing All categories shows every row again. | |
| AT-UC11-07 | Step 4, seller | Type `DIANA` in the seller box and press Enter. | Only Diana Ng's deals: upper and lower case don't matter. | |
| AT-UC11-08 | Step 4, filters combine | Keep `DIANA` and choose Others. | No rows, and "No deals match. A deal appears here once it is completed." | |
| AT-UC11-09 | A1 | Press Reset, type `zzz` in the seller box and press Enter. | "No deals match. A deal appears here once it is completed." and an empty table. | |
| AT-UC11-10 | A2 | Choose a From date that is after the To date. Then move To to a date after From. | "The date range is invalid. Choose a start date on or before the end date." appears in red under the dates, with no pop-up, and the rows shown before stay. It goes away as soon as the range is valid, and the rows update. | |
| AT-UC11-11 | Step 5, Reset | Set every filter, then press Reset. | Every filter is cleared and every completed deal is shown. | |
| AT-UC11-12 | Step 2 order kept | Click the Deal Date column heading. | Nothing is re-sorted: the rows stay newest deal date first. | |

## UC9 Report Listing (Task 1.4)

Report Listing starts from the detail screen's button. A listing can be reported whenever the buyer can open its
detail screen: any live listing, and any listing the buyer has a deal on, whatever its status (assumption 80). Only
the admin sees reports, so the seller never learns who reported a listing (SR4). Run the scenarios in order after a
reset: 02, 03, 07 and 09 use the report made in 01.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC9-01 | Basic flow, steps 1-9 | As `bob`: Browse → *MacBook Pro Charger 61W* → View Details → Report Listing. Choose "Misleading description", type "Says 61W but the label shows 30W.", press Submit Report. | The form is headed "Report 'MacBook Pro Charger 61W' to the admin", lists the six reasons in SR1's order (Prohibited item, Scam or fraud, Misleading description, Offensive content, Spam or duplicate, Other) and has none chosen. After submitting: "Your report of 'MacBook Pro Charger 61W' was submitted. The admin will review the listing." The detail screen stays as it was. | |
| AT-UC9-02 | Step 8, the admin is notified | Log in as `admin`, open Notification Board. | "Listing Reported": "'MacBook Pro Charger 61W' by Charlie Lee was reported (Misleading description) and needs moderation." The notification does not name `bob`. (VIEW in Manage Marketplace Listings shows the report with its reason and details once the admin's screen is wired.) | |
| AT-UC9-03 | A1 | As `bob`, press Report Listing on the charger again. | "You have already reported this listing." No form opens. | |
| AT-UC9-04 | A2, no reason | As `bob`, on the *IKEA Desk Lamp*: press Report Listing, type details, choose no reason, press Submit Report. | "Both a reason and details are required." The form comes back with the details still typed. | |
| AT-UC9-05 | A2, empty details | In the same form, choose a reason, replace the details with only spaces, press Submit Report. | The same message. The form comes back with the reason still chosen. | |
| AT-UC9-06 | A3 | In the form, press Cancel. Then press Report Listing again. | No message, and nothing is recorded. The form opens again, empty (not A1). | |
| AT-UC9-07 | SR3 is per buyer | Log in as `alice` and report the charger, with any reason and details. | Accepted: another buyer may report the same listing. `admin` now has two "Listing Reported" notifications about it. | |
| AT-UC9-08 | SR4 | Log in as `charlie`. Open Notification Board and every seller screen. | Nothing about the reports or who made them. | |
| AT-UC9-09 | SR3 across a restart | Close the app and start it again. As `bob`, press Report Listing on the charger. | "You have already reported this listing." | |
| AT-UC9-10 | A listing the buyer has a deal on (assumption 80) | As `bob`, open My Deals, and press View Listing on the completed *Organic Chemistry Textbook* (Sold). Press Report Listing. | The form opens: a buyer can report a listing they dealt on, even after it was sold. | |

## UC4 View Notifications: the board and the dot (all portals; assumptions registers 91 to 93)

The board is the same in all three portals: newest first, unread ones marked, Mark as read on each (UC4 step 3),
**Mark all as read** while anything is unread, and a **category filter** offering the categories on the user's board.
The red **dot** on the sidebar's Notifications button shows how many are unread; opening the board does not lower it,
only marking does (UC4 Special Requirement 3). Run in order after a reset. The automated checks are in
`NotificationServiceTest`, `HistoryAndNotificationsScreensTest` and `FormatsTest`.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC4-01 | The dot before the board | As `alice`, log in. | The sidebar's Notifications button has a red dot with **1**; pointing at the dot says "1 unread notification". | |
| AT-UC4-02 | Opening marks nothing | Open the Notification Board. | "1 unread notification": Charlie's acceptance of her offer, with Unread and Mark as read. The dot still says 1. | |
| AT-UC4-03 | A new notification raises the dot | Log out. As `charlie`, open Chats, open Alice's chat about the Calculus Textbook and send "The book is ready." Log out, and log in as `alice`. | The dot says **2**. On the board, a New Message notice is now first. | |
| AT-UC4-04 | The category filter | Open the category box, and choose New Message. | The box offers All categories, Offer Update and New Message, and nothing else. Only the New Message notice is shown; the heading still says 2 unread notifications. | |
| AT-UC4-05 | Marking one keeps the filter | Press Mark as read on it. | It turns grey and loses Unread and its button. The heading says 1 unread notification, the dot 1, and the box still says New Message. | |
| AT-UC4-06 | Mark all as read | Choose All categories, and press Mark all as read. | No question is asked. "No unread notifications"; no Mark as read button and no Mark all as read; the dot is gone. Both notices are still on the board. | |
| AT-UC4-07 | The timer raises it at once | Run AT-UC12-01 and keep Alice's dashboard open on any screen. | When the Due-Date Reminder arrives, the dot rises by 1 without a click. | |
| AT-UC4-08 | The other portals | As `charlie`, then `diana`, then `admin`, open the Notification Board. | The same board: the filter offers each user's own categories, and Mark all as read works as in AT-UC4-06. For `charlie` and `diana`, the dot counts and clears as in AT-UC4-01 to 06. *(The admin's dot appears once the admin portal adds its three lines: see the admin guide.)* | |

## UC4 View Notifications, admin: the accounts' history (Task 3.6; client question F4)

The admin's Notification Board lists every change to a buyer's or seller's account, in category "Account Change", so
it reads as the accounts' history (decided 24 Nov). A profile notice names the fields that changed, never their
values. An admin's own profile, and a save that changes nothing, send nothing. Users are not notified of their own
changes (the screen confirms them). Run in order after a reset; each scenario ends by opening `admin`'s Notification Board.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC4A-01 | New buyer account | Buyer portal → Register: username `nina`, full name `Nina Ng`, password and confirmation `Secure#123`. Then log in as `admin`. | "Account Change": "New buyer account: Nina Ng (@nina)." | |
| AT-UC4A-02 | New seller account | Seller portal → Register `sunny`, `Sunny Lam`, `Secure#123`. Then as `admin`. | "New seller account: Sunny Lam (@sunny)." | |
| AT-UC4A-03 | A buyer's own change | As `alice`: Manage Profile, full name `Alice Chan Tai`, new password and confirmation `New#Pass2`, Save. Open her Notification Board. Then as `admin`. | Alice sees "Profile updated" and no new notification. `admin`: "Alice Chan Tai (@alice) changed their profile: full name, password." The password does not appear. | |
| AT-UC4A-04 | A seller's own change | As `charlie`: Manage Profile, change the bio, tick one more meet-up location, Save. Then as `admin`. | "Charlie Lee (@charlie) changed their profile: bio, meet-up locations." | |
| AT-UC4A-05 | A save that changes nothing | As `bob`: Manage Profile, Save without changing anything. Then as `admin`. | "Profile updated" for Bob; no new Account Change notice for `admin`. | |
| AT-UC4A-06 | An admin's own profile | As `admin`: Manage Profile, change the full name, Save. Open the Notification Board. | No Account Change notice about it. | |
| AT-UC4A-07 | Admin edits an account | As `admin`: Manage All Users → Edit `bob` → full name `Bob Wong Jr` → OK. | `bob` gets "An administrator updated your account details." `admin`: "@admin edited the buyer account of Bob Wong Jr (@bob)." | Pass (2026-11-25, Jack) |
| AT-UC4A-08 | Admin deactivates  | Deactivate `bob`, reason `Spam`. | "@admin deactivated the buyer account of Bob Wong Jr (@bob). Reason: Spam" |  Pass (2026-11-25, Jack)|
| AT-UC4A-09 | Admin reactivates  | Activate `bob`. | "@admin reactivated the buyer account of Bob Wong Jr (@bob)." | Pass (2026-11-25, Jack) |
| AT-UC4A-10 | Admin deletes  | Delete `nina` (from 01), reason `Test account`. | "@admin deleted the buyer account of Nina Ng (@nina). Reason: Test account" |  Pass (2026-11-25, Jack)|

---

## Create Listing (Task 2.3)

Log in as `diana` and open **Create Listing**. Each scenario starts from a reset unless it says otherwise.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-2.3-01 | Basic flow, Sell | Type Sell; "Desk Fan"; Furniture; Used; "Three speeds, works."; price `150`; Hall 2 Lobby; Submit for Review. | "'Desk Fan' is now Pending Review. The admin has been notified." The card shows Pending Review. `admin` has "Diana Ng submitted 'Desk Fan' for review."; Diana has "Your listing 'Desk Fan' is pending review." | |
| AT-2.3-02 | Basic flow, Lend | Type Lend; "Desk Lamp"; Furniture; Like New; "LED bulb included."; duration `5` seconds; Hall 5 Lobby; Submit. | Accepted as Pending Review; the card shows the 5-second lending duration (short enough to run the auto-return test). | |
| AT-2.3-03 | Basic flow, Giveaway | Type Giveaway; fill the required fields; Submit. | Accepted; no price or duration is asked for. | |
| AT-2.3-04 | Steps 3-4, the type decides the form | Choose Sell, then Lend, then Giveaway. | Price is enabled only for Sell, duration only for Lend, and both are cleared when the type changes. | |
| AT-2.3-05 | A1 Required fields | Submit with the name empty; then no category; no condition; an empty description; no meet-up location. | "The item name cannot be empty.", "Please choose a category.", "Please choose the item's condition.", "The description cannot be empty.", "Please choose a meet-up location." Nothing is posted. | |
| AT-2.3-06 | A2 Price | Sell with price `0`, then `-5`, then `abc`, then `99.50`. | Each bad value: "The price must be a positive number, such as 180 or 99.50." The form keeps what was typed; `99.50` is accepted. | |
| AT-2.3-07 | A3 Duration | Lend with `4` seconds, then `abc`. | "The lending duration must be at least 5 seconds." / "The lending duration must be a whole number of seconds, minutes, hours or days." | |
| AT-2.3-08 | A4 Photos | Add two images; then (on another listing) choose seven. | The label lists the two photos, the first is the thumbnail. The seventh is refused: "A listing can have at most 6 photos." | |
| AT-2.3-09 | Leaving the form | Fill the form, then open another sidebar screen without submitting. | Nothing is posted; no notification is sent. | |
| AT-2.3-10 | Steps 11-13, review | As `admin`: Listing Review → Approve "Desk Fan". As `diana`: open My Listings and Notifications. | The listing is Live; Diana has "Your listing 'Desk Fan' is now live!" and the card shows Live. | |

## Manage Listings (Task 2.4)

Log in as `charlie` and open **My Listings**. After a reset he has the Sold *Organic Chemistry Textbook*, the Reserved *Calculus Textbook* and the Live *MacBook Pro Charger 61W*.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-2.4-01 | Step 1, the list | Open My Listings. | Three cards showing title, category/condition, type and status (Sold, Reserved, Live), each with Edit and Delete. | |
| AT-2.4-02 | Edit a Live listing with no open offers (client answer #5) | On the charger: Edit, change the name to "MacBook Pro Charger 61W (original)", Save Changes. | "Changes saved": "'MacBook Pro Charger 61W (original)' was updated." It **stays Live**, with no review and no Listing Submitted notice. | |
| AT-2.4-03 | Edit a Rejected listing (3.1.6) | As `diana`, create a listing; as `admin`, reject it with reason "Photo unclear"; as `diana`, Edit it and change the title; Save. | "…was updated and is Pending Review again." Diana and the admin get the Listing Submitted notices. | |
| AT-2.4-04 | A3 Edit with an open offer | As `alice`, make an offer on the charger; as `charlie`, Edit it and Save. | The confirmation warns that one open offer will be declined. After saving: "…was updated and is Pending Review again."; Alice gets "Your price offer … was declined because the seller changed the listing."; the offer is Declined. | |
| AT-2.4-05 | Step 2, delete (3.2.4) | On the charger (after AT-2.4-04), press Delete, OK. | The card shows Removed; the listing is gone from Browse; its record stays in My Listings as Removed; any open offers were declined with a notice. | |
| AT-2.4-06 | A2 Locked listing | On the Reserved calculus textbook, press Edit; then Delete. | "This listing can no longer be edited or deleted." Nothing changes (the same for Sold, Lent, Given and Removed). | |
| AT-2.4-07 | Cancel an edit | On a Live listing, Edit, change a field, then open another sidebar screen. | The listing keeps its old values; no notification is sent. | |

## Manage Offers & Requests (Task 2.5)

Log in as `diana`. After a reset Bob has a pending borrow request on the calculator, and *Mini Fridge* is waiting for review. Run 01-03 in order; 04-07 use `alice` and `charlie`.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-2.5-01 | Step 2, the inbox | Open Offers & Requests. | One card "Graphing Calculator TI-84": "Lend · Live · 1 offer waiting", Bob Wong (@bob), the proposed period, status Pending, and the buttons Accept, Decline, Counter, Open Chat. | |
| AT-2.5-02 | Step 3.3 Accept | Accept the borrow request, OK. | "Offer accepted"; the listing becomes Lent; the buyer's My Deals shows the deal Accepted; Bob has "Diana Ng accepted your borrow request for 'Graphing Calculator TI-84'." | |
| AT-2.5-03 | Step 3.4 Decline | On another open offer, press Decline, OK. | "Offer declined"; the status is Declined; the buyer is told. | |
| AT-2.5-04 | Step 3.5, counter a price (A4) | As `alice`, offer HK$ 200 on the charger; as `charlie`, Offers & Requests → Counter. Send `abc`, then `175`. | "abc": "The counter price must be a positive amount, such as 150 or 99.50." and the form comes back with `abc`. `175`: "Counter sent"; status Countered; Alice sees the counter and can agree. | |
| AT-2.5-05 | Step 3.5, counter a borrow period (A4) | Counter a borrow request with: a start yesterday; an end before the start; then a 5-second period. | "The counter period cannot start in the past." / "The counter period must end at least 5 seconds after it starts."; the form comes back with what was entered; the 5-second period is accepted. | |
| AT-2.5-06 | SR1 Claims cannot be countered | As `bob`, claim the lamp; as `diana`, open Offers & Requests. | The claim row has Accept and Decline only; there is no Counter button. | |
| AT-2.5-07 | Buttons follow the offer's state | Compare a Pending offer with a Countered one. | Pending: Accept, Decline and Counter are enabled. Countered: only Decline is enabled. | |
| AT-2.5-08 | Step 3.6, Open Chat | Press Open Chat; then "← Back to Offers & Requests". | The chat opens with the offer in its history; Back returns to the list with nothing marked. | |
| AT-2.5-09 | Chat → offers (assumptions register 71) | In a chat with an open offer, press "Respond in Offers & Requests (n)". | Offers & Requests opens with that chat's offer rows marked light yellow and the newest scrolled into view. | |

## View Seller Stats (Task 2.8)

Log in as `charlie` and open **Seller Stats** after a reset: the Sold chemistry book with one 5-star review, the Reserved calculus book and the Live charger; two offers received.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-2.8-01 | Steps 2-3, the numbers | Open Seller Stats. | Listings 3, Views 0, Offers received 2, Completed deals 1; "Average rating: 5.0 / 5 (1 rating)". | |
| AT-2.8-02 | Step 4, the bar chart | As `alice`, open the charger twice; as `charlie`, reopen Seller Stats. | Views 2; the bar chart shows the charger at 2 views and 0 offers; the other listings at 0. | |
| AT-2.8-03 | Offers in the chart | As `alice`, make an offer on the charger; reopen Seller Stats. | Offers received 3; the charger's Offers bar is 1; Views unchanged. | |
| AT-2.8-04 | Step 5, the pie chart | As `alice`, My Deals → calculus → Mark Complete; reopen Seller Stats. | Completed deals 2; the pie shows Sell (2); no Lend or Giveaway slice. | |
| AT-2.8-05 | A2 No ratings | As `diana`, open Seller Stats. | "Average rating: No ratings yet"; the card shows "—". | |
| AT-2.8-06 | A3 No completed deals | As `diana` (no completed deal), open Seller Stats. | The pie area says "No completed deals yet." | |
| AT-2.8-07 | A1 No listings | Register a new seller (or, in a dev run, log in as `seller`) and open Seller Stats. | Every number is 0, "No ratings yet", and the charts area says "There isn't enough data to draw charts yet." | |
| AT-2.8-08 | SR1 Hidden review | As `admin`, Seller Ratings → hide Charlie's review; as `charlie`, reopen Seller Stats. | Ratings 0 and "No ratings yet"; the hidden review is not counted. | |

## Manage Reviews (Task 2.9)

Log in as `charlie` and open **Reviews & Feedback**. After a reset there is one 5-star review from Bob on the *Organic Chemistry Textbook*.

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-2.9-01 | Step 1, the list | Open Reviews & Feedback. | "Average rating: 5.0 / 5 (1 rating)"; one card: 5 stars, Bob's comment, "by Bob Wong · Organic Chemistry Textbook · <date>", with Reply and Flag. | |
| AT-2.9-02 | Steps 2-5, reply | Press Reply, type "Thanks Bob, enjoy!", Send reply. Then as `bob`, open Notifications. | "Reply posted"; the card shows "Your reply: Thanks Bob, enjoy!" and the button is Replied (disabled). Bob has "Review Reply": "Charlie Lee replied to your review for 'Organic Chemistry Textbook': Thanks Bob, enjoy!". | |
| AT-2.9-03 | A2 One reply only | On a review that already has a reply, look at the Reply button. | It reads Replied and is disabled; the service refuses a second reply with "Only one reply is allowed per review." | |
| AT-2.9-04 | A3 Empty reply | Press Reply, clear the box, Send reply. | "The reply cannot be empty."; the form comes back with the empty box. | |
| AT-2.9-05 | Step 6, flag | Press Flag, OK. Then as `admin`, open Notification Board and Seller Ratings. | "Review flagged"; the card shows "Awaiting moderation" and the button is disabled. The admin has "@charlie flagged a review of 'Organic Chemistry Textbook' as inappropriate." and the review shows "Flagged by seller". | |
| AT-2.9-06 | A4 Already flagged | Try to flag a review that is already flagged. | The button is disabled; the service answers "This review is already awaiting moderation." | |
| AT-2.9-07 | A1 No reviews | As a seller with no reviews (a new account, or `seller` in a dev run), open Reviews & Feedback. | "No reviews yet. A buyer can review you after a completed deal." and "Average rating: No ratings yet". | |
| AT-2.9-08 | Deleted buyer (client answer #6) | As `admin`, Manage Users → delete `bob`; as `charlie`, reopen Reviews & Feedback. | The review is still shown, "by Deleted User", and the average is unchanged. Replying still works and no notification is sent. | |
| AT-2.9-09 | A5 Cancel | Press Reply, type something, Cancel. | No message, nothing saved; the card still offers Reply. | |

---

## Admin — Manage Marketplace (Tasks 3.2, 3.7)

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC3A-01 | Manage Listings | As `admin`, open Manage All Listings. Filter by Type=Sell and Status=Pending Review. | Only pending sale listings show. | Pass (2026-11-25, Jack) |
| AT-UC3A-02 | Approve/Reject | Approve a pending listing. Then Reject another with reason "Bad photo". | The listing turns Live or Rejected, and the seller's board shows "Listing Approved" or "Listing Rejected" with the reason. | Pass (2026-11-25, Jack) |
| AT-UC3A-03 | Remove/Edit | Edit a live listing's title. Then Remove it with reason "Violates policy". | Title updates. Removing declines open offers, notifies the seller, and every buyer with an open offer on the listing is told. | Pass (2026-11-25, Jack) |

## Admin — Manage Users (Task 3.3)

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC3U-01 | User Search | As `admin`, open Manage All Users. Type `alice` in search. | Only `alice` shows. Filtering by "Seller" or "Deactivated" works. | Pass (2026-11-25, Jack) |
| AT-UC3U-02 | User Details | Click View on `charlie`. | Shows active listings, completed deals, pending offers, and rating (hidden reviews not counted). | Pass (2026-11-25, Jack) |

## Admin — Manage Ratings (Task 3.8)

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC3R-01 | Seller Ratings List | As `admin`, open Seller Ratings. | Lists sellers with their average rating and completed deal count. Hidden reviews are not counted. | Pass (2026-11-25, Jack) |
| AT-UC3R-02 | View Reviews | Click View Reviews on `charlie`. | Lists charlie's reviews. Supports filtering by rating and date range. | Pass (2026-11-25, Jack)  |
| AT-UC3R-03 | Hide Review | Click Hide on a review, enter reason "Offensive". | The review is hidden. The buyer and seller get notifications. The average rating drops (if applicable). | Pass (2026-11-25, Jack) |

## Admin — Manage Deals (Task 3.5)

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC3D-01 | Deal Record | As `admin`, open All Deals Record. | Lists only accepted offers (in progress, completed, returned). Filter by Type, Status, Seller, Buyer. | Pass (2026-11-25, Jack)|
| AT-UC3D-02 | Deal Details & Export | Click View Details on a deal. Then Export CSV. | Details show buyer, seller, item, dates. CSV contains the filtered rows. | Pass (2026-11-25, Jack)|

## Bug Fix Regression Scenarios (R13, R14)

| ID | Flow | Steps | Expected result | Result |
|---|---|---|---|---|
| AT-UC4A-11 | R13: Hide review refreshes | As `admin`, open Ratings → charlie's Reviews. Click Hide on a review, submit a reason. | The list refreshes instantly. The row now shows the "Hidden" badge and the Hide button is gone. | Pass (2026-11-25, Jack) |
| AT-UC4A-12 | R14, R16, R17: Edit keeps a lend listing's duration | As `admin`, All Listings → + Add Listing: seller `diana`, type Lend, any title, category, condition and place, duration 36 hours; Save. Then Edit it, and Save without typing anything. | On Edit, the duration box is empty with the hint "Leave empty to keep 1 day 12 hours", and the unit box reads "days" in full. After Save, View shows the lending duration 1 day 12 hours. | Pass (26 Nov) |