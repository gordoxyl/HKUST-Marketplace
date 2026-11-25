package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.models.Admin;
import com.hkust.marketplace.models.SaleListing;
import com.hkust.marketplace.utils.SessionManager;

/**
 * Shared set-up for the admin tests (Task 3): a fresh store with one admin (ada), one seller
 * (sam) and one buyer (bea), plus helpers to log in as each role. Built on Fixtures, so no
 * new test tools are invented.
 */
public class AdminTestSupport {

    public final TestClock clock;
    public final DataStore store;
    public final Admin admin;
    public final Admin ada2;

    public AdminTestSupport() {
        clock = Fixtures.clock();
        store = Fixtures.store(clock);
        admin = Fixtures.admin(store, "ada");
        ada2 = Fixtures.admin(store, "ada2");
        Fixtures.seller(store, "sam");
        Fixtures.buyer(store, "bea");
    }

    public void loginAsAdmin()  { SessionManager.login(admin); }
    public void loginAsSeller() { SessionManager.login(store.findUser("sam")); }
    public void loginAsBuyer()  { SessionManager.login(store.findUser("bea")); }
    public void logout()        { SessionManager.logout(); }

    /** A sale listing by sam, Pending Review. */
    public SaleListing pendingSale() {
        return Fixtures.sale(store, "sam", 100);
    }

    /** A sale listing by sam, already approved (Live). */
    public SaleListing liveSale() {
        return Fixtures.live(Fixtures.sale(store, "sam", 100));
    }

    
}