package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.util.*;

/** Access-controlled buyer/seller conversations attached to marketplace listings. */
public final class MarketplaceMessagingService {
    private final Repository repo;
    private final NotificationService notifications;

    public MarketplaceMessagingService(Repository repo, NotificationService notifications) {
        this.repo = repo;
        this.notifications = notifications;
    }

    public List<MarketMessage> conversation(User user, int listingId, Integer otherUserId) {
        Listing listing = requireListing(listingId);
        EWasteItem item = requireItem(listing.itemId);
        boolean seller = item.ownerId == user.id;
        int other = seller ? (otherUserId == null ? -1 : otherUserId) : item.ownerId;
        if (seller && other < 0) throw new IllegalArgumentException("Select a buyer conversation.");
        if (!seller && otherUserId != null && otherUserId != item.ownerId)
            throw new IllegalArgumentException("Buyers can message only the item owner.");
        if (!seller && user.id != item.ownerId && !listing.active && !hasOrder(listingId, user.id))
            throw new IllegalArgumentException("This listing is no longer available.");
        if (seller && other != item.ownerId && !hasInteraction(listingId, other))
            throw new IllegalArgumentException("No conversation exists with that buyer.");

        List<MarketMessage> out = new ArrayList<>();
        for (MarketMessage m : repo.marketMessages())
            if (m.listingId == listingId && ((m.senderId == user.id && m.recipientId == other)
                    || (m.senderId == other && m.recipientId == user.id))) out.add(m);
        return out;
    }

    public MarketMessage send(User sender, int listingId, Integer recipientId, String text,
                              String fileName, String mediaType, byte[] fileData) {
        Listing listing = requireListing(listingId);
        EWasteItem item = requireItem(listing.itemId);
        boolean seller = item.ownerId == sender.id;
        int recipient;
        if (seller) {
            if (recipientId == null || recipientId == sender.id)
                throw new IllegalArgumentException("Choose a buyer to reply to.");
            recipient = recipientId;
            if (!listing.active && !hasOrder(listingId, recipient) && !hasMessages(listingId, recipient))
                throw new IllegalArgumentException("No conversation exists with that buyer.");
            if (listing.active && !hasMessages(listingId, recipient) && !hasOrder(listingId, recipient))
                throw new IllegalArgumentException("A buyer must contact you before you can reply.");
        } else {
            if (recipientId != null && recipientId != item.ownerId)
                throw new IllegalArgumentException("Buyers can message only the item owner.");
            if (!listing.active && !hasOrder(listingId, sender.id))
                throw new IllegalArgumentException("This listing is no longer available.");
            recipient = item.ownerId;
        }
        String content = text == null ? "" : text.trim();
        if (content.length() > 2000) throw new IllegalArgumentException("Messages must be 2000 characters or fewer.");
        boolean hasFile = fileData != null && fileData.length > 0;
        if (content.isEmpty() && !hasFile) throw new IllegalArgumentException("Enter a message or attach a file.");
        if (hasFile && (fileName == null || mediaType == null))
            throw new IllegalArgumentException("Attachment name and type are required.");

        MarketMessage message = new MarketMessage();
        message.listingId = listingId;
        message.senderId = sender.id;
        message.recipientId = recipient;
        message.text = content;
        message.fileName = hasFile ? fileName : "";
        message.mediaType = hasFile ? mediaType : "";
        message.fileData = hasFile ? fileData : null;
        repo.addMarketMessage(message);
        notifications.push(recipient, "New marketplace message from " + sender.fullName + " about " + item.name + ".");
        return message;
    }

    public MarketMessage requireMessage(User user, int listingId, int messageId) {
        for (MarketMessage m : repo.marketMessages()) {
            if (m.id == messageId && m.listingId == listingId && (m.senderId == user.id || m.recipientId == user.id))
                return m;
        }
        throw new IllegalArgumentException("Message attachment not found.");
    }

    public boolean mayViewPhoto(User user, Listing listing) {
        EWasteItem item = requireItem(listing.itemId);
        return listing.active || item.ownerId == user.id || hasOrder(listing.id, user.id);
    }

    private Listing requireListing(int id) {
        Listing l = repo.listing(id);
        if (l == null) throw new IllegalArgumentException("Listing #" + id + " was not found.");
        return l;
    }

    private EWasteItem requireItem(int id) {
        EWasteItem item = repo.item(id);
        if (item == null) throw new IllegalArgumentException("Marketplace item was not found.");
        return item;
    }

    private boolean hasOrder(int listingId, int userId) {
        for (Order o : repo.orders()) if (o.listingId == listingId && o.buyerId == userId) return true;
        return false;
    }

    private boolean hasMessages(int listingId, int userId) {
        for (MarketMessage m : repo.marketMessages())
            if (m.listingId == listingId && (m.senderId == userId || m.recipientId == userId)) return true;
        return false;
    }

    private boolean hasInteraction(int listingId, int userId) {
        return hasOrder(listingId, userId) || hasMessages(listingId, userId);
    }

    public List<Map<String, Object>> conversations(User user) {
        Map<String, MarketMessage> latest = new LinkedHashMap<>();
        for (MarketMessage m : repo.marketMessages()) {
            if (m.senderId != user.id && m.recipientId != user.id) continue;
            int other = m.senderId == user.id ? m.recipientId : m.senderId;
            latest.put(m.listingId + ":" + other, m);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (MarketMessage m : latest.values()) {
            Listing l = repo.listing(m.listingId);
            EWasteItem item = l == null ? null : repo.item(l.itemId);
            User other = repo.user(m.senderId == user.id ? m.recipientId : m.senderId);
            if (item == null || other == null) continue;
            out.add(map("listingId", m.listingId, "itemName", item.name, "otherUserId", other.id,
                    "otherUserName", other.fullName, "latestMessage", m.text.isEmpty() ? m.fileName : m.text,
                    "updatedAt", m.time.toString(), "unread", false));
        }
        out.sort((a, b) -> String.valueOf(b.get("updatedAt")).compareTo(String.valueOf(a.get("updatedAt"))));
        return out;
    }

    public Map<String, Object> messageView(MarketMessage m, Map<Integer, String> names) {
        return map("id", m.id, "listingId", m.listingId, "senderId", m.senderId, "senderName", names.get(m.senderId),
                "recipientId", m.recipientId, "recipientName", names.get(m.recipientId), "text", m.text,
                "fileName", m.fileName, "mediaType", m.mediaType,
                "downloadUrl", m.fileData == null ? null : "/api/market/" + m.listingId + "/messages/" + m.id + "/attachment",
                "createdAt", m.time.toString());
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
