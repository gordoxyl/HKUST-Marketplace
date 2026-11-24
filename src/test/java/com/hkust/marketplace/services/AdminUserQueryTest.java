package com.hkust.marketplace.services;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.testsupport.AdminTestSupport;
import com.hkust.marketplace.testsupport.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for Task 3.3 Manage All Users: the user list, its filters and the empty result (A2). */
class AdminUserQueryTest {

    private static final LocalDateTime DAY0 = Fixtures.START;

    private AdminTestSupport support;
    private AdminUserService service;
    private Seller sam;
    private Buyer bea;
    private Seller sue;
    private Buyer ben;

    @BeforeEach
    void setUp() {
        support = new AdminTestSupport();                 // admin ada, seller sam, buyer bea (all at START)
        sam = (Seller) support.store.findUser("sam");
        bea = (Buyer) support.store.findUser("bea");
        sue = Fixtures.seller(support.store, "sue");
        ben = Fixtures.buyer(support.store, "ben");
        service = new AdminUserService(support.store);
        support.loginAsAdmin();
    }

    @AfterEach
    void logOut() {
        support.logout();
    }

    /** Same users, in any order, each exactly once. */
    private static void assertSameUsers(List<User> actual, User... expected) {
        assertEquals(expected.length, actual.size(), "number of users");
        assertEquals(Set.of(expected), Set.copyOf(actual));
    }

    private void deactivate(User user) {
        support.store.transaction(() -> user.setActive(false));
    }

    // ---------- Task 3.3: list without filters ----------

    @Test
    void manageUsers_task33_noFilter_listsEveryBuyerAndSeller_butNoAdmin() {
        Fixtures.admin(support.store, "alan");            // a second admin, also excluded

        List<User> result = service.findUsers(UserFilter.none());

        assertSameUsers(result, sam, bea, sue, ben);
        assertTrue(result.stream().noneMatch(u -> u.getRole() == UserRole.ADMIN));
    }

    @Test
    void manageUsers_task33_nullFilterIsTheSameAsNoFilter() {
        assertSameUsers(service.findUsers(null), sam, bea, sue, ben);
    }

    @Test
    void manageUsers_task33_listIncludesDeactivatedUsers() {
        deactivate(bea);

        assertSameUsers(service.findUsers(UserFilter.none()), sam, bea, sue, ben);
    }

    @Test
    void manageUsers_task33_eachUserCarriesTheDisplayedFields() {
        User listed = service.findUsers(UserFilter.none().withRole(UserRole.BUYER)).stream()
                .filter(u -> u.getUsername().equals("bea")).findFirst().orElseThrow();

        assertEquals("bea", listed.getUsername());
        assertEquals("Buyer bea", listed.getFullName());
        assertEquals(UserRole.BUYER, listed.getRole());
        assertTrue(listed.isActive());
        assertEquals(DAY0, listed.getCreatedAt());
    }

    // ---------- Task 3.3: filters ----------

    @Test
    void manageUsers_task33_roleFilter_buyersOrSellers() {
        assertSameUsers(service.findUsers(UserFilter.none().withRole(UserRole.BUYER)), bea, ben);
        assertSameUsers(service.findUsers(UserFilter.none().withRole(UserRole.SELLER)), sam, sue);
    }

    @Test
    void manageUsers_task33_roleFilterAdmin_returnsAnEmptyList() {
        assertTrue(service.findUsers(UserFilter.none().withRole(UserRole.ADMIN)).isEmpty());
    }

    @Test
    void manageUsers_task33_statusFilter_activeOrDeactivated() {
        deactivate(bea);
        deactivate(sue);

        assertSameUsers(service.findUsers(UserFilter.none().withActive(true)), sam, ben);
        assertSameUsers(service.findUsers(UserFilter.none().withActive(false)), bea, sue);
    }

    @Test
    void manageUsers_task33_roleAndStatusCombine() {
        deactivate(bea);
        deactivate(sue);

        assertSameUsers(service.findUsers(
                UserFilter.none().withRole(UserRole.BUYER).withActive(false)), bea);
        assertSameUsers(service.findUsers(
                UserFilter.none().withRole(UserRole.SELLER).withActive(true)), sam);
    }

    @Test
    void manageUsers_task33_registrationRange_isInclusiveOnBothEnds() {
        support.clock.advance(Duration.ofDays(1));
        Buyer dan = Fixtures.buyer(support.store, "dan");        // registered day 1
        support.clock.advance(Duration.ofDays(1));
        Seller eve = Fixtures.seller(support.store, "eve");      // registered day 2
        LocalDateTime day1 = DAY0.plusDays(1);
        LocalDateTime day2 = DAY0.plusDays(2);

        assertSameUsers(service.findUsers(UserFilter.none().withRegistrationRange(day1, day1)), dan);
        assertSameUsers(service.findUsers(UserFilter.none().withRegistrationRange(day1, day2)), dan, eve);
        assertSameUsers(service.findUsers(UserFilter.none().withRegistrationRange(DAY0, day1)),
                sam, bea, sue, ben, dan);
        assertSameUsers(service.findUsers(UserFilter.none().withRegistrationRange(day2, null)), eve);
        assertSameUsers(service.findUsers(UserFilter.none().withRegistrationRange(null, DAY0)),
                sam, bea, sue, ben);
    }

    @Test
    void manageUsers_task33_registrationRange_justOutsideTheBoundaryIsExcluded() {
        support.clock.advance(Duration.ofDays(1));
        Buyer dan = Fixtures.buyer(support.store, "dan");
        LocalDateTime day1 = DAY0.plusDays(1);

        assertTrue(service.findUsers(UserFilter.none()
                .withRegistrationRange(day1.plusNanos(1), day1.plusDays(5))).isEmpty());
        assertTrue(service.findUsers(UserFilter.none()
                .withRegistrationRange(DAY0.minusDays(5), DAY0.minusNanos(1))).isEmpty());
        assertSameUsers(service.findUsers(UserFilter.none().withRegistrationRange(day1, day1)), dan);
    }

    @Test
    void manageUsers_task33_allFiltersCombine() {
        support.clock.advance(Duration.ofDays(1));
        Buyer dan = Fixtures.buyer(support.store, "dan");
        Buyer dora = Fixtures.buyer(support.store, "dora");
        deactivate(dan);
        LocalDateTime day1 = DAY0.plusDays(1);

        assertSameUsers(service.findUsers(UserFilter.none()
                .withRole(UserRole.BUYER).withActive(false).withRegistrationRange(day1, null)), dan);
        assertSameUsers(service.findUsers(UserFilter.none()
                .withRole(UserRole.BUYER).withActive(true).withRegistrationRange(day1, null)), dora);
    }

    // ---------- Task 3.3 A2: no results ----------

    @Test
    void manageUsers_task33_A2_noMatch_returnsAnEmptyList_notAnError() {
        assertTrue(service.findUsers(UserFilter.none().withActive(false)).isEmpty());   // nobody deactivated
        assertTrue(service.findUsers(UserFilter.none()
                .withRegistrationRange(DAY0.plusDays(10), DAY0.plusDays(11))).isEmpty());
    }

    @Test
    void manageUsers_task33_A2_rangeStartingAfterItsEnd_returnsAnEmptyList() {
        assertTrue(service.findUsers(UserFilter.none()
                .withRegistrationRange(DAY0.plusDays(2), DAY0)).isEmpty());
    }

    // ---------- Access control (UC2 Special Requirement 3) ----------

    @Test
    void manageUsers_task33_noSession_isDenied() {
        support.logout();

        assertThrows(AccessDeniedException.class, () -> service.findUsers(UserFilter.none()));
    }

    @Test
    void manageUsers_task33_buyerSession_isDenied() {
        support.loginAsBuyer();

        assertThrows(AccessDeniedException.class, () -> service.findUsers(UserFilter.none()));
    }

    @Test
    void manageUsers_task33_sellerSession_isDenied() {
        support.loginAsSeller();

        assertThrows(AccessDeniedException.class, () -> service.findUsers(UserFilter.none()));
    }
}