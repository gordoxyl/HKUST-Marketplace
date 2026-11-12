# Acceptance test scenarios

System and acceptance tests are black-box tests of the whole application through its screens
(COMP 3111 Lecture 14). Each scenario below comes from a use case in
`Diagrams and Use-Case Specifications.pdf`: one for the basic flow, one for each alternative flow,
and one for each special requirement that a user can see. They complement the automated tests,
which stop at the service layer.

**How to run them**

1. Close the app and reset the demo data: `scripts\reset-data.cmd` (Windows) or `sh scripts/reset-data.sh` (macOS).
2. Start the app: `.\mvnw.cmd clean javafx:run`.
3. Follow the steps, compare with the expected result, and fill in the Result column (Pass / Fail, date, tester).
   A Fail goes into `docs/regression-log.md` with the steps that reproduce it.

Demo accounts: buyers `alice`, `bob`; sellers `charlie`, `diana` (password `Demo@1234`); admin `admin` (`Admin@1234`).
Demo listings after a reset: *MacBook Pro Charger 61W* (sale, HK$ 250, Charlie, available), *IKEA Desk Lamp*
(giveaway, Diana, available), *Graphing Calculator TI-84* (lend, 90 days, Diana, available; Bob has a pending
borrow request), *Calculus Textbook (Stewart)* (sale, HK$ 180, Charlie, **Reserved** for Alice).

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
| AT-UC5-12 | SR4 agreeing to a counter | Needs the seller's Manage Offers screen (Task 2.5): as `charlie`, counter Alice's HK$ 200 offer with HK$ 230. As `alice`, open the chat and press "Agree to Counter" on that offer, then Submit. | The form starts with `230`. A new Pending offer of HK$ 230 appears; the first offer stays "Countered". | |
| AT-UC5-13 | Saved data | After any of the scenarios above, close the app and start it again. | The offers are still in the chats, and the lamp is still "Claim Pending". | |
