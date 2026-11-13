package com.ewaste.server.service;

import com.ewaste.model.SupportMessage;
import com.ewaste.model.User;
import com.ewaste.server.repo.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Private, account-level conversations between marketplace users and administrators. */
public final class SupportMessagingService {
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final Repository repo;
    private final NotificationService notifications;

    public SupportMessagingService(Repository repo, NotificationService notifications) {
        this.repo = repo;
        this.notifications = notifications;
    }

    public List<SupportMessage> userConversation(User user) {
        requireMarketplaceUser(user);
        return conversation(user.id, false);
    }

    public List<SupportMessage> adminConversation(User admin, int userId) {
        requireAdmin(admin);
        requireMarketplaceUser(repo.user(userId));
        return conversation(userId, true);
    }

    public List<Map<String, Object>> adminInbox(User admin) {
        requireAdmin(admin);
        Map<Integer, SupportMessage> latest = new LinkedHashMap<>();
        Map<Integer, Integer> unread = new LinkedHashMap<>();
        for (SupportMessage message : repo.supportMessages()) {
            SupportMessage current = latest.get(message.userId);
            if (current == null || compareMessages(message, current) > 0) latest.put(message.userId, message);
            if (message.senderId == message.userId && !message.readByAdmin)
                unread.merge(message.userId, 1, Integer::sum);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (User user : repo.users()) {
            if (user.isAdmin()) continue;
            SupportMessage last = latest.get(user.id);
            out.add(map("id", user.id, "userId", user.id, "fullName", user.fullName, "username", user.username,
                    "role", user.role.name(), "active", user.active, "hasMessages", last != null,
                    "latestMessage", last == null ? "" : preview(last.text),
                    "updatedAt", last == null ? "" : last.time.toString(),
                    "unread", unread.getOrDefault(user.id, 0)));
        }
        out.sort((a, b) -> {
            int byTime = String.valueOf(b.get("updatedAt")).compareTo(String.valueOf(a.get("updatedAt")));
            return byTime != 0 ? byTime : String.valueOf(a.get("username")).compareToIgnoreCase(String.valueOf(b.get("username")));
        });
        return out;
    }

    public SupportMessage sendFromUser(User sender, String text) {
        requireMarketplaceUser(sender);
        SupportMessage message = create(sender, sender.id, text);
        for (User admin : repo.users()) {
            if (admin.isAdmin() && admin.active)
                notifications.push(admin.id, "New support message from " + sender.fullName + ".");
        }
        return message;
    }

    public SupportMessage sendFromAdmin(User admin, int userId, String text) {
        requireAdmin(admin);
        requireMarketplaceUser(repo.user(userId));
        SupportMessage message = create(admin, userId, text);
        notifications.push(userId, "New reply from support.");
        return message;
    }

    public Map<String, Object> messageView(SupportMessage message) {
        User sender = repo.user(message.senderId);
        if (sender == null) throw new IllegalStateException("Support message sender was not found.");
        return map("id", message.id, "userId", message.userId, "senderId", message.senderId,
                "senderName", sender.fullName, "fromAdmin", sender.isAdmin(), "text", message.text,
                "createdAt", message.time.toString());
    }

    private List<SupportMessage> conversation(int userId, boolean adminViewing) {
        List<SupportMessage> out = new ArrayList<>();
        for (SupportMessage message : repo.supportMessages()) {
            if (message.userId != userId) continue;
            if (adminViewing && message.senderId == userId && !message.readByAdmin) {
                message.readByAdmin = true;
                repo.saveSupportMessage(message);
            } else if (!adminViewing && message.senderId != userId && !message.readByUser) {
                message.readByUser = true;
                repo.saveSupportMessage(message);
            }
            out.add(message);
        }
        out.sort(Comparator.comparing((SupportMessage message) -> message.time).thenComparingInt(message -> message.id));
        return out;
    }

    private SupportMessage create(User sender, int userId, String text) {
        String content = text == null ? "" : text.trim();
        if (content.isEmpty()) throw new IllegalArgumentException("Enter a message.");
        if (content.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("Messages must be " + MAX_MESSAGE_LENGTH + " characters or fewer.");
        SupportMessage message = new SupportMessage();
        message.userId = userId;
        message.senderId = sender.id;
        message.text = content;
        message.readByAdmin = sender.isAdmin();
        message.readByUser = !sender.isAdmin();
        return repo.addSupportMessage(message);
    }

    private static void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) throw new IllegalArgumentException("Admin access required.");
    }

    private static void requireMarketplaceUser(User user) {
        if (user == null || user.isAdmin()) throw new IllegalArgumentException("Marketplace user not found.");
    }

    private static int compareMessages(SupportMessage a, SupportMessage b) {
        int byTime = a.time.compareTo(b.time);
        return byTime != 0 ? byTime : Integer.compare(a.id, b.id);
    }

    private static String preview(String text) {
        String value = text.replace('\n', ' ').trim();
        return value.length() > 100 ? value.substring(0, 97) + "..." : value;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) out.put((String) values[i], values[i + 1]);
        return out;
    }
}
