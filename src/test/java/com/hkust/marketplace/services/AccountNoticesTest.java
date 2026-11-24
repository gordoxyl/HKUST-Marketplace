package com.hkust.marketplace.services;

import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.Buyer;
import com.hkust.marketplace.models.Seller;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the wording of the accounts' history on the admins' Notification Board (Task 3.6; client
 * question F4, decided on 24 Nov 2026).
 *
 * <p><b>Equivalence classes</b> (Lecture 13): a buyer's and a seller's account; one changed field and
 * several; each admin action, with and without a reason.
 */
class AccountNoticesTest {

    private final Admin ada = new Admin("ada", "Admin Ada", "hash");
    private final Buyer bea = new Buyer("bea", "Bea Chan", "hash", null);
    private final Seller sam = new Seller("sam", "Sam Lee", "hash");

    @Test
    void aNewAccount_namesItsRoleAndTheUser() {
        assertEquals("New buyer account: Bea Chan (@bea).", AccountNotices.created(bea));
        assertEquals("New seller account: Sam Lee (@sam).", AccountNotices.created(sam));
    }

    @Test
    void aProfileChange_namesTheFieldsThatChanged_neverTheirValues() {
        assertEquals("Bea Chan (@bea) changed their profile: password.",
                AccountNotices.profileChanged(bea, List.of(AccountNotices.PASSWORD)));
        assertEquals("Sam Lee (@sam) changed their profile: full name, bio, meet-up locations.",
                AccountNotices.profileChanged(sam, List.of(AccountNotices.FULL_NAME, AccountNotices.BIO,
                        AccountNotices.MEETUP_LOCATIONS)));
    }

    @Test
    void anAdminsAction_namesTheAdminAndTheAccount_andTheReasonWhereOneIsRequired() {
        assertEquals("@ada edited the buyer account of Bea Chan (@bea).", AccountNotices.edited(bea, ada));
        assertEquals("@ada deactivated the seller account of Sam Lee (@sam). Reason: Spam",
                AccountNotices.deactivated(sam, ada, "Spam"));
        assertEquals("@ada reactivated the seller account of Sam Lee (@sam).", AccountNotices.activated(sam, ada));
        assertEquals("@ada deleted the buyer account of Bea Chan (@bea). Reason: Fraud",
                AccountNotices.deleted(bea, ada, "Fraud"));
    }
}
