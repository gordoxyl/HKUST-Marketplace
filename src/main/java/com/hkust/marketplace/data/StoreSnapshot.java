package com.hkust.marketplace.data;

import com.hkust.marketplace.models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything in a DataStore at one moment, as plain lists: what a {@link Storage} saves and
 * loads. Records refer to each other by id or username, so the lists contain no cycles.
 */
public class StoreSnapshot {

    /** The next number DataStore will use for an id, so ids stay unique after loading. */
    public int nextId = 1;

    public List<User> users = new ArrayList<>();
    /** Usernames of deleted accounts, which are never given out again. Empty in files saved before accounts could be deleted. */
    public List<String> retiredUsernames = new ArrayList<>();
    public List<Listing> listings = new ArrayList<>();
    public List<Offer> offers = new ArrayList<>();
    public List<ChatThread> threads = new ArrayList<>();
    public List<Review> reviews = new ArrayList<>();
    public List<Report> reports = new ArrayList<>();
    public List<Notification> notifications = new ArrayList<>();
    public List<ListingActionRecord> listingActions = new ArrayList<>();
    public List<AuditLogEntry> auditLog = new ArrayList<>();
}
