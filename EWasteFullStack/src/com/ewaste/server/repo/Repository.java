package com.ewaste.server.repo;

import com.ewaste.model.*;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Storage abstraction. Implementations must behave like a database: objects returned are copies, so
 * a change only persists after the matching save*() call.
 */
public interface Repository extends AutoCloseable {
    // users
    User addUser(User u);
    User user(int id);
    User userByName(String username);
    List<User> users();
    void saveUser(User u);

    // items (history is only loaded by item(id))
    EWasteItem addItem(EWasteItem i);
    EWasteItem item(int id);
    List<EWasteItem> items();
    void saveItem(EWasteItem i);
    void addHistory(int itemId, String line);

    // market
    Listing addListing(Listing l);
    Listing listing(int id);
    List<Listing> listings();
    void saveListing(Listing l);
    Order addOrder(Order o);
    List<Order> orders();
    MarketMessage addMarketMessage(MarketMessage message);
    List<MarketMessage> marketMessages();
    SupportMessage addSupportMessage(SupportMessage message);
    List<SupportMessage> supportMessages();
    void saveSupportMessage(SupportMessage message);

    // notifications
    Notification addNotification(Notification n);
    List<Notification> notifications();
    void saveNotification(Notification n);

    // recycling
    RecyclingBatch addBatch(RecyclingBatch b);
    List<RecyclingBatch> batches();

    // recycler auctions
    Auction addAuction(Auction a);
    Auction auction(int id);
    List<Auction> auctions();
    void saveAuction(Auction a);
    AuctionBid addAuctionBid(AuctionBid b);
    void saveAuctionBid(AuctionBid b);
    List<AuctionBid> auctionBids(int auctionId);
    List<AuctionBid> auctionBids();
    AuctionHistoryEntry addAuctionHistory(AuctionHistoryEntry entry);
    List<AuctionHistoryEntry> auctionHistory(int auctionId);

    // campaigns (collection drives)
    Campaign addCampaign(Campaign c);
    Campaign campaign(int id);
    List<Campaign> campaigns();
    void saveCampaign(Campaign c);

    // eco challenges
    EcoChallenge addEcoChallenge(EcoChallenge challenge);
    EcoChallenge ecoChallenge(int id);
    List<EcoChallenge> ecoChallenges();
    void saveEcoChallenge(EcoChallenge challenge);
    ChallengeEnrollment addChallengeEnrollment(ChallengeEnrollment enrollment);
    ChallengeEnrollment challengeEnrollment(int challengeId, int userId);
    List<ChallengeEnrollment> challengeEnrollments();
    void saveChallengeEnrollment(ChallengeEnrollment enrollment);
    Achievement addAchievement(Achievement achievement);
    List<Achievement> achievements();
    ChallengeReward addChallengeReward(ChallengeReward reward);
    List<ChallengeReward> challengeRewards();

    /** Runs the work as one unit (a SQL transaction for databases). */
    <T> T atomic(Callable<T> work);

    /** Human-readable description of the backing store, for the startup log. */
    String describe();

    @Override void close();
}
