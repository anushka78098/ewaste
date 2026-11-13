package com.ewaste.server;

import com.ewaste.common.Json;
import com.ewaste.model.*;
import com.ewaste.server.mail.Mailer;
import com.ewaste.server.mail.SmtpMailer;
import com.ewaste.server.repo.Repository;
import com.ewaste.server.service.*;
import com.ewaste.server.service.DecisionPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** REST API: routing, authentication, JSON in/out. See docs/API.md for the endpoint list. */
public class Api implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(Api.class.getName());

    enum Access { PUBLIC, USER, ADMIN, RECYCLER }

    interface Handler { Object handle(Ctx c) throws Exception; }

    /** Non-JSON response (used for the CSV export). */
    static final class Raw {
        final String body, type, filename;
        Raw(String body, String type, String filename) { this.body = body; this.type = type; this.filename = filename; }
    }
    static final class BinaryRaw {
        final byte[] body; final String type, filename;
        BinaryRaw(byte[] body, String type, String filename) { this.body = body; this.type = type; this.filename = filename; }
    }

    static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;
        ApiException(int status, String msg) { super(msg); this.status = status; }
    }

    private static final class Route {
        final String method; final String[] segs; final Access access; final Handler handler;
        Route(String method, String path, Access access, Handler handler) {
            this.method = method; this.segs = path.substring(1).split("/"); this.access = access; this.handler = handler;
        }
        Map<String, String> match(String[] parts) {
            if (parts.length != segs.length) return null;
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < segs.length; i++) {
                if (segs[i].startsWith("{")) params.put(segs[i].substring(1, segs[i].length() - 1), parts[i]);
                else if (!segs[i].equals(parts[i])) return null;
            }
            return params;
        }
    }

    /** Per-request context handed to route handlers. */
    final class Ctx {
        User user;
        String token;
        Map<String, String> path = new HashMap<>();
        Map<String, String> query = new HashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();

        int pid(String name) {
            try { return Integer.parseInt(path.get(name)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + name + " in URL."); }
        }
        String str(String key) {
            Object v = body.get(key);
            if (v == null || String.valueOf(v).trim().isEmpty()) throw new IllegalArgumentException("'" + key + "' is required.");
            return String.valueOf(v).trim();
        }
        String opt(String key) { Object v = body.get(key); return v == null ? null : String.valueOf(v); }
        double dbl(String key) {
            Object v = body.get(key);
            if (v instanceof Number) return ((Number) v).doubleValue();
            try { return Double.parseDouble(str(key)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("'" + key + "' must be a number."); }
        }
        int integer(String key) {
            Object v = body.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            try { return Integer.parseInt(str(key)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("'" + key + "' must be a whole number."); }
        }
        boolean bool(String key) {
            Object v = body.get(key);
            if (v instanceof Boolean) return (Boolean) v;
            throw new IllegalArgumentException("'" + key + "' must be true or false.");
        }
        <E extends Enum<E>> E en(Class<E> t, String key) { return parseEnum(t, str(key), key); }
        String q(String key) { return query.get(key); }
    }

    static <E extends Enum<E>> E parseEnum(Class<E> t, String v, String what) {
        try { return Enum.valueOf(t, v.trim().toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid " + what + ": " + v); }
    }

    private final Repository repo;
    private final Sessions sessions = new Sessions();
    private final AuthService auth;
    private final NotificationService notes;
    private final ItemService items;
    private final DecisionEngine decisionEngine;
    private final MarketService market;
    private final AuctionService auctions;
    private final RecyclingService recycling;
    private final ReportService reports;
    private final CampaignService campaigns;
    private final EcoChallengeService ecoChallenges;
    private final AnalyticsService analytics;
    private final MarketplaceMessagingService marketMessages;
    private final SupportMessagingService supportMessages;
    private final List<Route> routes = new ArrayList<>();
    private final Object lock = new Object();
    private final LoginThrottle throttle = new LoginThrottle(5, 5 * 60_000L);
    private boolean logRequests = true;

    private final Mailer mailer;

    public Api(Repository repo) { this(repo, null, DecisionPolicy.defaults()); }

    public Api(Repository repo, Mailer mailer) { this(repo, mailer, DecisionPolicy.defaults()); }

    public Api(Repository repo, Mailer mailer, DecisionPolicy policy) {
        this.repo = repo;
        this.mailer = mailer;
        this.decisionEngine = new DecisionEngine(policy);
        this.auth = new AuthService(repo);
        this.notes = new NotificationService(repo, mailer);
        if (mailer instanceof SmtpMailer) notes.setEmailMarketAlerts(((SmtpMailer) mailer).config().marketAlerts);
        this.items = new ItemService(repo, notes, decisionEngine);
        this.market = new MarketService(repo, notes, items);
        this.auctions = new AuctionService(repo, notes, items);
        this.recycling = new RecyclingService(repo, notes, items, auctions);
        this.reports = new ReportService(repo);
        this.campaigns = new CampaignService(repo, notes);
        this.ecoChallenges = new EcoChallengeService(repo, notes);
        this.analytics = new AnalyticsService(repo, campaigns);
        this.marketMessages = new MarketplaceMessagingService(repo, notes);
        this.supportMessages = new SupportMessagingService(repo, notes);
        registerRoutes();
    }

    public void setLogRequests(boolean b) { logRequests = b; }

    private void route(String method, String path, Access a, Handler h) { routes.add(new Route(method, path, a, h)); }

    // ------------------------------------------------------------------------------------------ routes
    private void registerRoutes() {
        route("GET", "/api/health", Access.PUBLIC, c -> map("status", "ok", "store", repo.describe()));
        route("GET", "/api/meta", Access.PUBLIC, c -> meta());
        route("GET", "/api/track/{code}", Access.PUBLIC, c -> {
            int id = Tracking.parse(c.path.get("code"));
            EWasteItem i = id < 0 ? null : repo.item(id);
            if (i == null || !Tracking.code(i).equalsIgnoreCase(c.path.get("code").trim()))
                throw new ApiException(404, "No item found for that tracking code.");
            return map("trackingCode", Tracking.code(i), "categoryLabel", i.category.label, "status", i.status.name(), "statusLabel", i.status.label,
                    "plan", i.decision == null ? null : i.decision.name(), "pickupDate", i.pickupDate == null ? null : i.pickupDate.toString(),
                    "steps", Tracking.steps(i));
        });
        route("GET", "/api/passport/{code}", Access.PUBLIC, c -> {
            String code = c.path.get("code");
            int id = Tracking.parse(code);
            EWasteItem item = id < 0 ? null : repo.item(id);
            if (item == null || !Tracking.code(item).equalsIgnoreCase(code.trim())
                    || (item.status != ItemStatus.LISTED && item.status != ItemStatus.SOLD))
                throw new ApiException(404, "No public circularity passport is available for that tracking code.");
            Listing listing = null;
            for (Listing candidate : repo.listings()) if (candidate.itemId == item.id) { listing = candidate; break; }
            return CircularityPassport.create(item, listing);
        });

        // ---- auth
        route("POST", "/api/auth/register", Access.PUBLIC, c -> {
            User u = auth.register(c.str("username"), c.str("password"), c.str("fullName"), c.str("email"), c.opt("phone"), c.opt("address"));
            notes.push(u.id, "Welcome to E-Waste Manager! Submit your first item to start earning reward points.");
            return map("user", Dto.user(u));
        });
        route("POST", "/api/auth/login", Access.PUBLIC, c -> {
            String key = c.opt("username") == null ? "" : c.opt("username").trim().toLowerCase();
            long wait = throttle.lockedFor(key, System.currentTimeMillis());
            if (wait > 0) throw new ApiException(429, "Too many failed sign-in attempts. Try again in " + ((wait + 59_999) / 60_000) + " minute(s).");
            User u;
            try {
                u = auth.login(c.opt("username"), c.opt("password"));
            } catch (IllegalArgumentException e) {
                throttle.failure(key, System.currentTimeMillis());
                throw e;
            }
            throttle.success(key);
            return map("token", sessions.create(u.id), "user", Dto.user(u));
        });
        route("POST", "/api/auth/logout", Access.USER, c -> { sessions.remove(c.token); return map("ok", true); });

        // ---- profile
        route("GET", "/api/me", Access.USER, c -> {
            Map<String, Object> m = Dto.user(c.user);
            m.put("unread", notes.unread(c.user.id));
            return m;
        });
        route("PUT", "/api/me", Access.USER, c ->
                Dto.user(auth.updateProfile(c.user, c.opt("fullName"), c.opt("email"), c.opt("phone"), c.opt("address"),
                        c.body.containsKey("emailOptOut") ? c.bool("emailOptOut") : null)));
        route("GET", "/api/me/support", Access.USER, c -> {
            if (c.user.isAdmin()) throw new ApiException(403, "Use the administrator support inbox.");
            List<Object> out = new ArrayList<>();
            for (SupportMessage message : supportMessages.userConversation(c.user))
                out.add(supportMessages.messageView(message));
            return out;
        });
        route("POST", "/api/me/support", Access.USER, c -> {
            if (c.user.isAdmin()) throw new ApiException(403, "Use the administrator support inbox.");
            return supportMessages.messageView(supportMessages.sendFromUser(c.user, c.str("text")));
        });
        route("POST", "/api/me/password", Access.USER, c -> {
            auth.changePassword(c.user, c.str("oldPassword"), c.str("newPassword"));
            return map("ok", true);
        });
        route("GET", "/api/me/impact", Access.USER, c -> reports.impact(c.user));
        route("GET", "/api/leaderboard", Access.USER, c -> reports.leaderboard(10));
        route("GET", "/api/me/dashboard", Access.USER, c -> analytics.userDashboard(c.user, LocalDate.now()));
        route("GET", "/api/me/market/dashboard", Access.USER, c -> analytics.marketDashboard(c.user));
        route("GET", "/api/me/certificate", Access.USER, c ->
                new Raw(analytics.certificateHtml(c.user, LocalDate.now()), "text/html; charset=utf-8", "impact-certificate.html"));
        route("GET", "/api/campaigns", Access.USER, c -> campaigns.list(true, LocalDate.now()));
        route("GET", "/api/eco/challenges", Access.USER, c -> ecoChallenges.userView(c.user, LocalDate.now()));
        route("POST", "/api/eco/challenges/{id}/enroll", Access.USER, c -> {
            ChallengeEnrollment enrollment = ecoChallenges.enroll(c.user, c.pid("id"), LocalDate.now());
            return map("challengeId", enrollment.challengeId, "status", enrollment.status.name());
        });
        route("GET", "/api/eco/leaderboard", Access.USER, c -> ecoChallenges.leaderboard(
                parseEnum(EcoChallengePeriod.class, c.q("period") == null ? "WEEKLY" : c.q("period"), "period"), LocalDate.now()));
        route("POST", "/api/decision/report", Access.USER, c -> Dto.decisionReport(decisionEngine.assess(
                c.en(Category.class, "category"), c.integer("ageYears"), c.en(Condition.class, "condition"),
                optionalDouble(c.body.get("estimatedResaleValue"), "estimatedResaleValue"),
                optionalDouble(c.body.get("repairCost"), "repairCost"),
                optionalDouble(c.body.get("recyclingValue"), "recyclingValue"),
                optionalBoolean(c.body.get("hazardous"), "hazardous"),
                optionalDouble(c.body.get("weightKg"), "weightKg"))));

        // ---- items (user)
        route("POST", "/api/items", Access.USER, c -> {
            EWasteItem i = items.submit(c.user, c.str("name"), c.opt("brand"), c.en(Category.class, "category"), c.integer("ageYears"),
                    c.dbl("weightKg"), c.en(Condition.class, "condition"), c.opt("description"));
            return itemDto(i);
        });
        route("GET", "/api/items/mine", Access.USER, c -> itemList(items.search(null, null, c.user.id)));
        route("GET", "/api/items/{id}", Access.USER, c -> {
            EWasteItem i = items.require(c.pid("id"));
            if (i.ownerId != c.user.id && !c.user.isAdmin()) throw new ApiException(403, "That is not your item.");
            return itemDto(i);
        });

        // ---- market
        route("GET", "/api/market", Access.USER, c -> {
            String cat = c.q("category");
            double max = 0;
            try { if (c.q("maxPrice") != null && !c.q("maxPrice").isEmpty()) max = Double.parseDouble(c.q("maxPrice")); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("maxPrice must be a number."); }
            return listingList(market.browse(c.q("q"), cat == null || cat.isEmpty() ? null : parseEnum(Category.class, cat, "category"), max));
        });
        route("GET", "/api/market/{id}/photo", Access.USER, c -> {
            Listing l = market.activeListingOrAny(c.pid("id"));
            if (!marketMessages.mayViewPhoto(c.user, l)) throw new ApiException(403, "You cannot view this listing photo.");
            if (l.photoData == null) throw new ApiException(404, "This listing has no photo.");
            return new BinaryRaw(l.photoData, l.photoMediaType, l.photoFileName);
        });
        route("POST", "/api/market/{id}/photo", Access.USER, c -> {
            Listing l = market.activeListingOrAny(c.pid("id"));
            EWasteItem i = items.require(l.itemId);
            if (i.ownerId != c.user.id) throw new ApiException(403, "Only the item owner can upload a listing photo.");
            if (!l.active) throw new IllegalArgumentException("Only active listings can have photos updated.");
            Upload upload = upload(c, true);
            l.photoFileName = upload.fileName;
            l.photoMediaType = upload.mediaType;
            l.photoData = upload.data;
            repo.saveListing(l);
            return Dto.listing(l, i, ownerNames().getOrDefault(i.ownerId, "?"));
        });
        route("GET", "/api/market/{id}/messages", Access.USER, c -> {
            Integer with = optionalInt(c.q("with"), "with");
            List<Object> out = new ArrayList<>();
            Map<Integer, String> names = ownerNames();
            for (MarketMessage m : marketMessages.conversation(c.user, c.pid("id"), with))
                out.add(marketMessages.messageView(m, names));
            return out;
        });
        route("POST", "/api/market/{id}/messages", Access.USER, c -> {
            Integer recipient = c.body.containsKey("recipientId") ? c.integer("recipientId") : null;
            Upload file = upload(c, false);
            MarketMessage m = marketMessages.send(c.user, c.pid("id"), recipient, c.opt("text"),
                    file == null ? null : file.fileName, file == null ? null : file.mediaType, file == null ? null : file.data);
            return marketMessages.messageView(m, ownerNames());
        });
        route("GET", "/api/market/{id}/messages/{messageId}/attachment", Access.USER, c -> {
            MarketMessage m = marketMessages.requireMessage(c.user, c.pid("id"), c.pid("messageId"));
            if (m.fileData == null) throw new ApiException(404, "This message has no attachment.");
            return new BinaryRaw(m.fileData, m.mediaType, m.fileName);
        });
        route("GET", "/api/messages", Access.USER, c -> marketMessages.conversations(c.user));
        route("POST", "/api/market/{id}/buy", Access.USER, c -> {
            int pts = c.body.containsKey("points") ? c.integer("points") : 0;
            Order o = market.buy(c.user, c.pid("id"), pts);
            EWasteItem sold = items.require(o.itemId);
            ecoChallenges.refreshForUser(sold.ownerId, LocalDate.now());
            return Dto.order(o, sold.name);
        });
        route("GET", "/api/orders/mine", Access.USER, c -> {
            List<Object> out = new ArrayList<>();
            for (Order o : market.ordersOf(c.user.id)) out.add(Dto.order(o, items.require(o.itemId).name));
            return out;
        });

        // ---- recycler auctions
        route("GET", "/api/auctions", Access.RECYCLER, c -> {
            List<Object> out = new ArrayList<>();
            for (Auction a : auctions.visibleTo(c.user)) {
                List<AuctionBid> bids = repo.auctionBids(a.id);
                AuctionBid mine = auctions.myBid(a.id, c.user.id);
                Map<String, Object> row = Dto.auction(a, items.require(a.itemId), bids.size(), auctions.winner(a.id));
                row.put("myBid", mine == null ? null : Dto.auctionBid(mine, c.user));
                out.add(row);
            }
            return out;
        });
        route("POST", "/api/auctions/{id}/bids", Access.RECYCLER, c -> {
            LocalDate collectionDate;
            try { collectionDate = LocalDate.parse(c.str("proposedCollectionDate")); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("Proposed collection date must be yyyy-mm-dd."); }
            AuctionBid bid = auctions.placeBid(c.user, c.pid("id"), c.dbl("amount"), collectionDate, c.str("recyclingMethod"));
            return Dto.auctionBid(bid, c.user);
        });

        // ---- notifications
        route("GET", "/api/notifications", Access.USER, c -> {
            List<Object> out = new ArrayList<>();
            for (Notification n : notes.forUser(c.user.id)) out.add(Dto.notification(n));
            return out;
        });
        route("POST", "/api/notifications/read", Access.USER, c -> { notes.markAllRead(c.user.id); return map("ok", true); });

        // ---- admin: items
        route("GET", "/api/admin/items", Access.ADMIN, c -> {
            String st = c.q("status");
            return itemList(items.search(st == null || st.isEmpty() ? null : parseEnum(ItemStatus.class, st, "status"), c.q("q"), null));
        });
        route("POST", "/api/admin/items/{id}/approve", Access.ADMIN, c -> {
            LocalDate d;
            try { d = LocalDate.parse(c.str("pickupDate")); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("Pickup date must be yyyy-mm-dd."); }
            items.approve(c.pid("id"), c.en(Decision.class, "decision"), d, c.opt("note"));
            return itemDto(items.require(c.pid("id")));
        });
        route("POST", "/api/admin/items/{id}/reject", Access.ADMIN, c -> {
            items.reject(c.pid("id"), c.str("reason"));
            return itemDto(items.require(c.pid("id")));
        });
        route("POST", "/api/admin/items/{id}/collect", Access.ADMIN, c -> {
            int pts = items.markCollected(c.pid("id"));
            EWasteItem collected = items.require(c.pid("id"));
            ecoChallenges.refreshForUser(collected.ownerId, LocalDate.now());
            return map("pointsAwarded", pts, "item", itemDto(collected));
        });
        route("POST", "/api/admin/items/{id}/decision", Access.ADMIN, c -> {
            if (auctions.reserved(c.pid("id")))
                throw new IllegalArgumentException("Cancel or award the item's recycler auction before changing its recycling plan.");
            items.changeDecision(c.pid("id"), c.en(Decision.class, "decision"));
            return itemDto(items.require(c.pid("id")));
        });
        route("POST", "/api/admin/items/{id}/refurbish/start", Access.ADMIN, c -> {
            items.startRefurbish(c.pid("id"), c.dbl("repairCost"));
            return itemDto(items.require(c.pid("id")));
        });
        route("POST", "/api/admin/items/{id}/refurbish/complete", Access.ADMIN, c -> {
            items.completeRefurbish(c.pid("id"));
            return itemDto(items.require(c.pid("id")));
        });

        // ---- admin: market
        route("POST", "/api/admin/listings", Access.ADMIN, c -> {
            Listing l = market.publish(c.integer("itemId"), c.dbl("price"));
            EWasteItem i = items.require(l.itemId);
            ecoChallenges.refreshForUser(i.ownerId, LocalDate.now());
            return Dto.listing(l, i, ownerNames().getOrDefault(i.ownerId, "?"));
        });
        route("PUT", "/api/admin/listings/{id}", Access.ADMIN, c -> {
            market.updatePrice(c.pid("id"), c.dbl("price"));
            Listing l = repo.listing(c.pid("id"));
            EWasteItem i = items.require(l.itemId);
            return Dto.listing(l, i, ownerNames().getOrDefault(i.ownerId, "?"));
        });
        route("DELETE", "/api/admin/listings/{id}", Access.ADMIN, c -> { market.delist(c.pid("id")); return map("ok", true); });

        // ---- admin: recycler auctions
        route("GET", "/api/admin/auctions/eligible", Access.ADMIN, c -> itemList(auctions.eligibleItems()));
        route("GET", "/api/admin/auctions", Access.ADMIN, c -> {
            List<Object> out = new ArrayList<>();
            for (Auction a : auctions.all()) out.add(auctionDto(a));
            return out;
        });
        route("POST", "/api/admin/auctions", Access.ADMIN, c -> {
            LocalDateTime closesAt;
            try { closesAt = LocalDateTime.parse(c.str("closesAt")); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("Closing time must use yyyy-mm-dd'T'HH:mm or ISO local date-time format."); }
            Auction a = auctions.publish(c.user, c.integer("itemId"), c.dbl("minimumBid"), closesAt);
            return auctionDto(a);
        });
        route("GET", "/api/admin/auctions/{id}/bids", Access.ADMIN, c -> {
            List<Object> out = new ArrayList<>();
            for (AuctionBid bid : auctions.bids(c.pid("id"))) out.add(Dto.auctionBid(bid, repo.user(bid.recyclerId)));
            return out;
        });
        route("GET", "/api/admin/auctions/{id}/history", Access.ADMIN, c -> {
            List<Object> out = new ArrayList<>();
            for (AuctionHistoryEntry h : auctions.history(c.pid("id")))
                out.add(Dto.auctionHistory(h, h.actorId == null ? null : repo.user(h.actorId)));
            return out;
        });
        route("POST", "/api/admin/auctions/{id}/award", Access.ADMIN, c -> {
            Auction a = auctions.award(c.user, c.pid("id"), c.integer("bidId"));
            return auctionDto(a);
        });
        route("POST", "/api/admin/auctions/{id}/cancel", Access.ADMIN, c -> auctionDto(auctions.cancel(c.user, c.pid("id"))));

        // ---- admin: recycling
        route("GET", "/api/admin/recycling", Access.ADMIN, c -> {
            List<Object> bs = new ArrayList<>();
            List<RecyclingBatch> all = repo.batches();
            for (int k = all.size() - 1; k >= 0; k--) bs.add(Dto.batch(all.get(k)));
            return map("eligible", recycling.eligible().size(), "queuedRegular", recycling.queued(false).size(),
                    "queuedHazardous", recycling.queued(true).size(), "batches", bs);
        });
        route("POST", "/api/admin/recycling/queue-all", Access.ADMIN, c -> map("queued", recycling.queueAllEligible()));
        route("POST", "/api/admin/recycling/batches", Access.ADMIN, c -> {
            RecyclingBatch batch = recycling.processBatch(c.str("partner"), c.bool("hazardous"));
            Set<Integer> owners = new HashSet<>();
            for (int itemId : batch.itemIds) {
                EWasteItem item = repo.item(itemId);
                if (item != null) owners.add(item.ownerId);
            }
            for (int ownerId : owners) ecoChallenges.refreshForUser(ownerId, LocalDate.now());
            return Dto.batch(batch);
        });

        // ---- admin: users, announcements, reports
        route("GET", "/api/admin/users", Access.ADMIN, c -> {
            Map<Integer, Integer> counts = new HashMap<>();
            for (EWasteItem i : repo.items()) counts.merge(i.ownerId, 1, Integer::sum);
            List<Object> out = new ArrayList<>();
            for (User u : repo.users()) {
                if (u.isAdmin()) continue;
                Map<String, Object> m = Dto.user(u);
                m.put("itemCount", counts.getOrDefault(u.id, 0));
                out.add(m);
            }
            return out;
        });
        route("GET", "/api/admin/support", Access.ADMIN, c -> supportMessages.adminInbox(c.user));
        route("GET", "/api/admin/support/{userId}", Access.ADMIN, c -> {
            List<Object> out = new ArrayList<>();
            for (SupportMessage message : supportMessages.adminConversation(c.user, c.pid("userId")))
                out.add(supportMessages.messageView(message));
            return out;
        });
        route("POST", "/api/admin/support/{userId}", Access.ADMIN, c ->
                supportMessages.messageView(supportMessages.sendFromAdmin(c.user, c.pid("userId"), c.str("text"))));
        route("POST", "/api/admin/users/{id}/active", Access.ADMIN, c -> { auth.setActive(c.pid("id"), c.bool("active")); return map("ok", true); });
        route("POST", "/api/admin/users/{id}/role", Access.ADMIN, c ->
                Dto.user(auth.setRole(c.pid("id"), c.en(Role.class, "role"))));
        route("POST", "/api/admin/users/{id}/points", Access.ADMIN, c ->
                Dto.user(auth.awardPoints(c.pid("id"), c.integer("points"), notes)));
        route("POST", "/api/admin/announce", Access.ADMIN, c -> map("sent", notes.announce(c.str("message"))));
        route("GET", "/api/admin/mail", Access.ADMIN, c -> mailer == null ? map("enabled", false) : mailer.status());
        route("POST", "/api/admin/mail/test", Access.ADMIN, c -> {
            if (mailer == null || !mailer.enabled()) throw new IllegalArgumentException("Email is not enabled. Configure config/mail.properties and restart the server.");
            String to = c.str("to");
            try { mailer.sendNow(to, "E-Waste Manager test email", "This is a test email from your E-Waste Manager server.\nIf you can read this, email notifications are working."); }
            catch (java.io.IOException e) { throw new IllegalArgumentException("Could not send: " + e.getMessage()); }
            return map("ok", true, "to", to);
        });
        route("GET", "/api/admin/stats", Access.ADMIN, c -> reports.stats());
        route("GET", "/api/admin/analytics", Access.ADMIN, c -> {
            int days = 30;
            try { if (c.q("days") != null && !c.q("days").isEmpty()) days = Integer.parseInt(c.q("days")); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("days must be a whole number."); }
            return analytics.adminAnalytics(days, LocalDate.now());
        });
        route("GET", "/api/admin/campaigns", Access.ADMIN, c -> campaigns.list(false, LocalDate.now()));
        route("POST", "/api/admin/campaigns", Access.ADMIN, c -> {
            LocalDate end;
            try { end = LocalDate.parse(c.str("endDate")); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("End date must be yyyy-mm-dd."); }
            Campaign k = campaigns.create(c.str("name"), c.opt("description"), c.dbl("goalKg"), end, LocalDate.now());
            return campaigns.view(k, repo.items(), LocalDate.now());
        });
        route("POST", "/api/admin/campaigns/{id}/end", Access.ADMIN, c -> { campaigns.end(c.pid("id")); return map("ok", true); });
        route("GET", "/api/admin/eco/challenges", Access.ADMIN, c -> ecoChallenges.adminView(LocalDate.now()));
        route("POST", "/api/admin/eco/challenges", Access.ADMIN, c ->
                ecoChallengeView(ecoChallenges.create(c.user, c.str("title"), c.opt("description"),
                        c.en(EcoChallengeType.class, "type"), c.en(EcoChallengePeriod.class, "period"),
                        c.dbl("targetValue"), c.integer("rewardPoints"), LocalDate.now()), LocalDate.now()));
        route("POST", "/api/admin/eco/challenges/{id}/end", Access.ADMIN, c -> {
            ecoChallenges.end(c.user, c.pid("id"));
            return map("ok", true);
        });
        route("GET", "/api/admin/export", Access.ADMIN, c -> new Raw(reports.exportCsv(), "text/csv; charset=utf-8", "items.csv"));
    }

    private Map<String, Object> ecoChallengeView(EcoChallenge challenge, LocalDate today) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", challenge.id);
        result.put("title", challenge.title);
        result.put("description", challenge.description);
        result.put("type", challenge.type.name());
        result.put("period", challenge.period.name());
        result.put("targetValue", challenge.targetValue);
        result.put("rewardPoints", challenge.rewardPoints);
        result.put("startDate", challenge.startDate.toString());
        result.put("endDate", challenge.endDate.toString());
        result.put("status", challenge.active && !today.isAfter(challenge.endDate) ? "ACTIVE" : "EXPIRED");
        return result;
    }

    private Object meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> cats = new ArrayList<>();
        for (Category c : Category.values())
            cats.add(map("name", c.name(), "label", c.label, "hazardous", c.hazardous, "baseValue", c.baseValue,
                    "maxReuseAge", c.maxReuseAge, "pointsPerKg", c.pointsPerKg));
        m.put("categories", cats);
        List<Object> conds = new ArrayList<>();
        for (Condition c : Condition.values()) conds.add(map("name", c.name(), "label", c.label));
        m.put("conditions", conds);
        List<Object> decs = new ArrayList<>();
        for (Decision d : Decision.values()) decs.add(map("name", d.name(), "label", d.label));
        m.put("decisions", decs);
        List<Object> sts = new ArrayList<>();
        for (ItemStatus s : ItemStatus.values()) sts.add(map("name", s.name(), "label", s.label));
        m.put("statuses", sts);
        m.put("pointValue", MarketService.POINT_VALUE);
        m.put("maxPointDiscount", MarketService.MAX_POINT_DISCOUNT);
        return m;
    }

    // ------------------------------------------------------------------------------------------ helpers
    private Map<Integer, String> ownerNames() {
        Map<Integer, String> names = new HashMap<>();
        for (User u : repo.users()) names.put(u.id, u.fullName + " (" + u.username + ")");
        return names;
    }

    private Map<String, Object> itemDto(EWasteItem i) {
        // reload so history is included for freshly changed items
        EWasteItem full = repo.item(i.id);
        return Dto.item(full == null ? i : full, ownerNames(), decisionEngine);
    }

    private List<Object> itemList(List<EWasteItem> list) {
        Map<Integer, String> names = ownerNames();
        List<Object> out = new ArrayList<>();
        for (EWasteItem i : list) out.add(Dto.item(i, names, decisionEngine));
        return out;
    }

    private static Double optionalDouble(Object value, String name) {
        if (value == null) return null;
        try { return value instanceof Number ? ((Number) value).doubleValue() : Double.valueOf(String.valueOf(value)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("'" + name + "' must be a number."); }
    }

    private static Boolean optionalBoolean(Object value, String name) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        throw new IllegalArgumentException("'" + name + "' must be true or false.");
    }

    private List<Object> listingList(List<Listing> list) {
        List<Object> out = new ArrayList<>();
        Map<Integer, String> names = ownerNames();
        for (Listing l : list) {
            EWasteItem item = items.require(l.itemId);
            out.add(Dto.listing(l, item, names.getOrDefault(item.ownerId, "?")));
        }
        return out;
    }

    private Map<String, Object> auctionDto(Auction auction) {
        List<AuctionBid> bids = repo.auctionBids(auction.id);
        return Dto.auction(auction, items.require(auction.itemId), bids.size(), auctions.winner(auction.id));
    }

    private static Integer optionalInt(String value, String name) {
        if (value == null || value.isEmpty()) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("'" + name + "' must be a whole number."); }
    }

    private static final class Upload {
        final String fileName, mediaType;
        final byte[] data;
        Upload(String fileName, String mediaType, byte[] data) { this.fileName = fileName; this.mediaType = mediaType; this.data = data; }
    }

    private static Upload upload(Ctx c, boolean imageOnly) {
        String encoded = c.opt("attachmentBase64");
        if (encoded == null || encoded.isEmpty()) return null;
        if (encoded.length() > 5_600_000) throw new ApiException(413, "File is too large (maximum 4 MB).");
        byte[] data;
        try { data = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Attachment must be valid base64 data."); }
        if (data.length == 0) throw new IllegalArgumentException("Attachment is empty.");
        if (data.length > 4_000_000) throw new ApiException(413, "File is too large (maximum 4 MB).");
        String mediaType = c.opt("attachmentType");
        String fileName = safeFileName(c.opt("attachmentName"));
        if (mediaType == null) throw new IllegalArgumentException("Attachment type is required.");
        boolean pdf = data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-';
        boolean valid = (("image/png".equals(mediaType) || "image/jpeg".equals(mediaType)) && validImage(data, mediaType))
                || (!imageOnly && "application/pdf".equals(mediaType) && pdf);
        if (!valid) throw new IllegalArgumentException(imageOnly
                ? "Listing photos must be a valid PNG or JPEG image."
                : "Attachments must be a valid PNG, JPEG, or PDF file.");
        return new Upload(fileName, mediaType, data);
    }

    private static boolean validImage(byte[] data, String mediaType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if ("image/png".equals(mediaType) && !"png".equalsIgnoreCase(format)) return false;
                if ("image/jpeg".equals(mediaType) && !"jpeg".equalsIgnoreCase(format) && !"jpg".equalsIgnoreCase(format)) return false;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 6000 || height > 6000 || (long) width * height > 20_000_000L) return false;
                return reader.read(0) != null;
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("Attachment name is required.");
        String base = fileName.trim().replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        if (base.isEmpty() || base.equals(".") || base.equals("..")) throw new IllegalArgumentException("Invalid attachment name.");
        return base.length() > 100 ? base.substring(base.length() - 100) : base;
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------------------------------ HTTP
    @Override public void handle(HttpExchange ex) throws IOException {
        int status = 200;
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        try {
            ex.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            if (method.equals("OPTIONS")) { send(ex, 204, "application/json", new byte[0], null); return; }

            String[] parts = path.length() > 1 ? path.substring(1).split("/") : new String[0];
            Route found = null;
            Map<String, String> params = null;
            boolean pathExists = false;
            for (Route r : routes) {
                Map<String, String> p = r.match(parts);
                if (p == null) continue;
                pathExists = true;
                if (r.method.equals(method)) { found = r; params = p; break; }
            }
            if (found == null) throw new ApiException(pathExists ? 405 : 404, pathExists ? "Method not allowed." : "Not found: " + path);

            Ctx ctx = new Ctx();
            ctx.path = params;
            ctx.query = parseQuery(ex.getRequestURI().getRawQuery());
            String authz = ex.getRequestHeaders().getFirst("Authorization");
            if (authz != null && authz.startsWith("Bearer ")) ctx.token = authz.substring(7).trim();
            byte[] raw = readBody(ex.getRequestBody());
            if (raw.length > 0) {
                try { ctx.body = Json.parseObject(new String(raw, StandardCharsets.UTF_8)); }
                catch (IllegalArgumentException e) { throw new ApiException(400, "Invalid JSON: " + e.getMessage()); }
            }

            final Route route = found;
            Object result;
            synchronized (lock) {
                if (route.access != Access.PUBLIC) {
                    int uid = sessions.userId(ctx.token);
                    User u = uid < 0 ? null : repo.user(uid);
                    if (u == null || !u.active) throw new ApiException(401, "Please log in again.");
                    if (route.access == Access.ADMIN && !u.isAdmin()) throw new ApiException(403, "Admin access required.");
                    if (route.access == Access.RECYCLER && u.role != Role.RECYCLER)
                        throw new ApiException(403, "Recycler access required.");
                    ctx.user = u;
                }
                repo.atomic(() -> { auctions.closeExpired(); return null; });
                try {
                    result = repo.atomic(() -> route.handler.handle(ctx));
                    notes.flushMail();
                } catch (IllegalStateException e) {
                    // repositories wrap checked exceptions; unwrap handler errors that were deliberate
                    if (e.getCause() instanceof ApiException) throw (ApiException) e.getCause();
                    if (e.getCause() instanceof IllegalArgumentException) throw (IllegalArgumentException) e.getCause();
                    throw e;
                } finally {
                    notes.discardMail();      // no-op after a successful flush; drops mail from failed requests
                }
            }
            if (result instanceof Raw) {
                Raw r = (Raw) result;
                send(ex, 200, r.type, r.body.getBytes(StandardCharsets.UTF_8), r.filename);
            } else if (result instanceof BinaryRaw) {
                BinaryRaw r = (BinaryRaw) result;
                send(ex, 200, r.type, r.body, r.filename);
            } else {
                send(ex, 200, "application/json; charset=utf-8", Json.write(result).getBytes(StandardCharsets.UTF_8), null);
            }
        } catch (ApiException e) {
            status = e.status;
            error(ex, e.status, e.getMessage());
        } catch (IllegalArgumentException e) {
            status = 400;
            error(ex, 400, e.getMessage());
        } catch (Throwable t) {
            status = 500;
            LOGGER.log(Level.SEVERE, "Unhandled API request failure.", t);
            error(ex, 500, "Internal server error.");
        } finally {
            if (logRequests) System.out.println(status + " " + method + " " + path);
            ex.close();
        }
    }

    private void error(HttpExchange ex, int status, String msg) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.write(map("error", msg)).getBytes(StandardCharsets.UTF_8), null);
    }

    private void send(HttpExchange ex, int status, String type, byte[] body, String filename) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        if (filename != null) ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        if (status == 204) { ex.sendResponseHeaders(204, -1); return; }
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }

    private static byte[] readBody(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > 6_000_000) throw new ApiException(413, "Request body too large.");
        }
        return bo.toByteArray();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            try {
                String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), "UTF-8");
                String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                m.put(k, v);
            } catch (java.io.UnsupportedEncodingException ignored) { }
        }
        return m;
    }
}
