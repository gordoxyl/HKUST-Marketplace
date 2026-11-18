# Client (TA) answers

The TA, acting as our client, answered Group 44's questions about the specification on **16 Nov 2026**, "based on the current updated requirements". This page records the answers, what each one means for our build, who acts on it, and whether that is done.

**Where an answer differs from `Diagrams and Use-Case Specifications.pdf`, the answer wins.** The answers below are summarized, not quoted.

Status as of 17 Nov 2026: **done** = the code already behaves this way; **to do** = a change is still needed.

## The answers

| # | Answer | What it means for us | Owner | Status |
|---|---|---|---|---|
| 1 | Deal History's "Outcome" is the deal's final result, such as Completed or Returned. Self-returns and automatic returns need not be told apart. | Showing "Auto-returned" is allowed but not required. | Buyer (Gordon) | done |
| 2 | Only buyers make price offers, borrow requests and claims; sellers Accept, Decline or Counter. Agreeing to a counter by making a new offer with the counter's terms is acceptable. | Confirms the current design: a buyer cannot accept a counter directly (UC5 SR4). | All | done |
| 3 | A lending deal is completed once the item is returned, by the buyer or automatically. The rate-seller prompt must also follow a return, as it follows Mark Complete. | Prompt to rate the seller after a self-return and after an automatic return too, not only after Mark Complete. | Buyer (Gordon, UC10) | to do |
| 4 | Admin All Deals "Start Date": for a loan, the agreed borrow start; for a sale or giveaway, the date the offer was accepted. Buyer Deal History "Deal Date": the completion date (marked complete or returned). | All Deals uses the accepted borrow request's start date for loans and `getAcceptedDate()` for sales and giveaways. Deal History uses `getCompletedDate()`. | Admin (Jack, 3.5); Buyer (Gordon, UC11) | to do |
| 5 | A seller may edit a rejected listing and resubmit it. Editing a live listing with no active offers does not need to go back to Pending Review. Changing the listing type is not forbidden, but a new listing is recommended. | Rejected → edit → Pending Review already works. **An edit of a Live listing with no open offers keeps it Live** (this overrides Manage Listings 3.1.6). The type stays fixed after posting. | Seller (Daniel, PR #11 follow-up); docs (Gordon) | to do (seller code) |
| 6 | Deleting a user by archiving their data and then removing it is acceptable. Chat retention applies to active users and ongoing deals. Reviews written by a deleted buyer are kept and shown as "Deleted User". Reports filed by or against a deleted user are kept. | `DataStore.deleteAccount` must **keep the reviews the user wrote**, and screens must show their author as "Deleted User". Reports are already kept. What "chat retention" means for chats with an active user is still open (below). | Shared core (Gordon); Admin (Jack, Delete User) | to do |
| 7 | Reports have no resolved or dismissed status. A report is handled by acting on the listing (take-down, Task 3.7). | Confirms `Report` without a status; the admin handles a report with Remove Listing. | Admin (Jack) | done |
| 8 | One review per completed deal: borrowing the same item twice gives two reviews. A written review and a 1–5 rating are both expected; the written part is not optional. | Reviews stay keyed by deal. **The written comment becomes required** (UC10 says "may"). | Buyer (Gordon, UC10) | to do (comment) |

## Still open

To ask the client:

- A Reserved deal that gets stuck when its buyer is deleted or deactivated: the seller can't edit or delete it, and only the admin's Remove ends it.
- When the admin removes a Reserved or Lent listing, whether the buyer with the accepted deal is told.
- Whether the admin may edit Sold, Given or Removed listings. Renaming a sold item also renames it in the buyer's deal history.
- Whether the admin may add a listing for a deactivated seller (today it is accepted, and hidden until the seller is reactivated).
- What happens to a chat with an active user, or about an ongoing deal, when the other participant is deleted.
- Editing a Live listing that does have open offers (today: the offers are declined and the listing goes back to Pending Review).
- Whether a lend listing's lending duration is a maximum for borrow requests.
- Whether reviews show who wrote them.

For the team to decide:

- How to prompt the buyer to rate the seller after an automatic return, which happens while the buyer may not be using the app.
- Whether "Deleted User" reviews still count in the seller's average rating.
- The minimum length of the written review.

When the client answers more, update this page and the guides that state the old rule.
