# Client (TA) answers

The TA, acting as our client, answered Group 44's questions about the specification on **16 Nov 2026**, "based on the current updated requirements". This page records the answers, what each one means for our build, who acts on it, and whether that is done. On **21 Nov 2026** the TA also replied to our four follow-up questions (see below).

**Where an answer differs from `Diagrams and Use-Case Specifications.pdf`, the answer wins.** The answers below are summarized, not quoted.

Status as of 21 Nov 2026: **done** = the code already behaves this way; **to do** = a change is still needed.

## The answers

| # | Answer | What it means for us | Owner | Status |
|---|---|---|---|---|
| 1 | Deal History's "Outcome" is the deal's final result, such as Completed or Returned. Self-returns and automatic returns need not be told apart. | Showing "Auto-returned" is allowed but not required. | Buyer (Gordon) | done |
| 2 | Only buyers make price offers, borrow requests and claims; sellers Accept, Decline or Counter. Agreeing to a counter by making a new offer with the counter's terms is acceptable. | Confirms the current design: a buyer cannot accept a counter directly (UC5 SR4). | All | done |
| 3 | A lending deal is completed once the item is returned, by the buyer or automatically. The rate-seller prompt must also follow a return, as it follows Mark Complete. | Prompt to rate the seller after a self-return and after an automatic return too, not only after Mark Complete. After an automatic return the prompt pops up if the buyer is using the app; otherwise the return notification says "You can rate the seller from My Deals." (team decision, 18 Nov). | Buyer (Gordon, UC10) | done |
| 4 | Admin All Deals "Start Date": for a loan, the agreed borrow start; for a sale or giveaway, the date the offer was accepted. Buyer Deal History "Deal Date": the completion date (marked complete or returned). | All Deals uses the accepted borrow request's start date for loans and `getAcceptedDate()` for sales and giveaways. Deal History uses `getCompletedDate()`. | Admin (Jack, 3.5); Buyer (Gordon, UC11) | done (Buyer: Deal History, 21 Nov); to do (Admin: All Deals) |
| 5 | A seller may edit a rejected listing and resubmit it. Editing a live listing with no active offers does not need to go back to Pending Review. Changing the listing type is not forbidden, but a new listing is recommended. | Rejected → edit → Pending Review already works. **An edit of a Live listing with no open offers keeps it Live** (this overrides Manage Listings 3.1.6). The type stays fixed after posting. | Seller (Daniel, PR #11 follow-up); docs (Gordon) | to do (seller code) |
| 6 | Deleting a user by archiving their data and then removing it is acceptable. Chat retention applies to active users and ongoing deals. Reviews written by a deleted buyer are kept; they "can be shown as from a 'Deleted User'". Reports filed by or against a deleted user are kept. | `DataStore.deleteAccount` **keeps the reviews the user wrote** (they still count in the rating), and `DataStore.getReviewerName(review)` gives "Deleted User" as their writer's name for every screen. Reports are kept. Chats with the deleted user are archived in the Audit Log snapshot and then deleted, which this answer allows. | Shared core (Gordon); Admin (Jack: Delete User, Seller Ratings 3.8); Seller (Daniel: Reviews 2.9) | done (shared core, 20 Nov); to do (Delete User, and the review screens to use `getReviewerName`) |
| 7 | Reports have no resolved or dismissed status. A report is handled by acting on the listing (take-down, Task 3.7). | Confirms `Report` without a status; the admin handles a report with Remove Listing. | Admin (Jack) | done |
| 8 | One review per completed deal: borrowing the same item twice gives two reviews. A written review and a 1–5 rating are both expected; the written part is not optional. | Reviews stay keyed by deal. **The written comment is required** (UC10 says "may"). | Buyer (Gordon, UC10) | done |

## Follow-up questions (asked 20 Nov, answered 21 Nov)

Before asking, each earlier question was checked against the task list (`F2026_Project_Tasks_Non_H_Track_Groups.md`) and the answers above; only these four were left. The TA replied on **21 Nov 2026** that they are edge cases too extreme to need a deeper implementation, and that the project demo will not check them. None got a ruling, so **what we built stands as the team's decision**, and we ask no more edge-case questions.

| # | Question | Decision (behaviour as built) | Owner | Status |
|---|---|---|---|---|
| F1 | **A deal that cannot be finished.** If a buyer is deleted or deactivated after their purchase or claim is accepted (or the seller is deactivated during it), may the seller close it, or cancel it and put the listing back to Live (Task 2.5)? | No new action. The listing stays Reserved until the admin removes it (Task 3.7). | Shared core (Gordon); Seller (Daniel); Admin (Jack) | no change |
| F2 | **The admin removing a listing that is not Live.** May the admin remove a Reserved or Lent listing, and is the buyer whose deal was already accepted told (Tasks 1.8 and 3.7)? | Remove is allowed in every status except Sold, Given and Removed. The accepted deal can still be completed. Only buyers with open offers are notified. | Admin (Jack) | no change |
| F3 | **Lending duration.** Is it the longest period a buyer may request (Tasks 2.3 and 1.5)? | A guide, not a maximum: it fills in the borrow form, and the seller counters or declines a longer request. | Buyer (Gordon); Seller (Daniel) | no change |
| F4 | **"User account changes / updates" on the admin's board (Task 3.6).** Which events are meant? | No list from the client, so the team chooses (last list below). Today the admins are notified only of submitted listings, and a user's own profile change notifies no one. | Admin (Jack) | for the team to decide |

Settled by the requirements or by an answer above (not asked):

- Whether the admin may edit Sold, Given or Removed listings: yes. Task 3.7: "manage every listing regardless of status … Edit any listing details". Whether past deals keep their old details is for the team to decide.
- Chats with an active user after the other participant is deleted: answer 6. Retention applies to active users and ongoing deals; once a user is deleted, archiving is fine.
- Editing a Live listing that has open offers: Task 2.4 already gives the choice: restrict it, or confirm and notify the affected buyers. Answer 5 covers the case with no open offers. Whether it then goes back to Pending Review is for the team to decide.
- Which screens name a review's writer: only those whose spec steps already show the buyer, so answer 6's "Deleted User" applies there and nowhere else (team decision, 20 Nov):
  - **named:** the seller's View Reviews (Task 2.9, step 2: "the buyer's name") and the admin's 6.5 ("Buyer"), using `DataStore.getReviewerName`;
  - **not named:** the buyer's listing detail and seller profile (UC8 step 7 and A3 list rating, comment, date and reply only).
  This is not a privacy rule. The spec's only anonymity rule is for reports: the seller is never told who reported a listing (UC9 Special Requirement 4).
- Whether the admin must be able to edit user accounts: yes. Task 3.3: "View, edit, deactivate, or permanently delete". Which fields is for the team to decide.
- What "filtering by user" means for Seller Ratings (Task 3.8): for the team to decide. Filtering by both the seller and the reviewing buyer covers either reading.
- Whether the All Deals Record lists deals still in progress: yes, implied by Task 3.5's Status and Return/Complete Date columns and by answer 4's Start Date.
- Reviews after a deletion: answer 6 keeps a deleted buyer's reviews. A kept review is a review, so it still counts.

For the team to decide:

- ~~How to prompt the buyer after an automatic return~~: decided 18 Nov, see answer 3.
- The minimum length of the written review (today: any text that is not blank; answer 8 only says it is required).
- Whether the admin may add a listing for a deactivated seller (today: accepted, hidden until the seller is reactivated).
- ~~Whether completed deals keep a listing's old details after an admin edit~~: decided 21 Nov (Gordon) for Deal History. No copy is kept when a deal is completed, so a past deal shows its listing's current title and category.
- Whether an edited Live listing with open offers goes back to Pending Review (today: the offers are declined and it does).
- Which user fields the admin can edit (Task 3.3).
- The Seller Ratings user filter: by seller, by buyer, or both.
- Whether reviews about a deleted seller stay in the admin's ratings overview (today: they are kept).
- Which account events reach the admin's Notification Board (Task 3.6, question F4). Keep it small, as the client advised.

No more questions are planned: on 21 Nov the client asked us not to go deeper on edge cases. Decide such cases in the team and add them to the list above. If the client does answer more, update this page and the guides that state the old rule.
