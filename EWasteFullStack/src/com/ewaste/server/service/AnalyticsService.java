package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Numbers behind the dashboards: admin analytics + alerts, the user dashboard (tiers, badges), the impact certificate. */
public class AnalyticsService {
    // Reward tiers by kg of e-waste handed in.
    private static final String[] TIER_NAMES = {"Newcomer", "Bronze Recycler", "Silver Recycler", "Gold Recycler", "Platinum Recycler"};
    private static final double[] TIER_KG = {0, 5, 25, 100, 250};

    private final Repository repo;
    private final CampaignService campaigns;

    public AnalyticsService(Repository repo, CampaignService campaigns) {
        this.repo = repo;
        this.campaigns = campaigns;
    }

    public Map<String, Object> marketDashboard(User user) {
        List<Map<String, Object>> purchases = new ArrayList<>();
        List<Order> allOrders = repo.orders();
        Map<Integer, Double> salesByListing = new HashMap<>();
        for (Order o : allOrders) salesByListing.merge(o.listingId, o.amountPaid, Double::sum);
        double spent = 0;
        int buyerCount = 0;
        for (Order o : allOrders) {
            if (o.buyerId != user.id) continue;
            EWasteItem item = repo.item(o.itemId);
            if (item == null) continue;
            User seller = repo.user(item.ownerId);
            purchases.add(map("orderId", o.id, "itemName", item.name, "sellerName", seller == null ? "Unknown" : seller.fullName,
                    "amountPaid", o.amountPaid, "discount", o.discount, "createdAt", o.time.toString()));
            spent += o.amountPaid;
            buyerCount++;
        }
        Collections.reverse(purchases);

        List<Map<String, Object>> sellerItems = new ArrayList<>();
        double sales = 0;
        int soldCount = 0, activeCount = 0;
        Map<Integer, Listing> listingByItem = new HashMap<>();
        for (Listing listing : repo.listings()) listingByItem.put(listing.itemId, listing);
        for (EWasteItem item : repo.items()) {
            if (item.ownerId != user.id && !user.isAdmin()) continue;
            Listing listing = listingByItem.get(item.id);
            if (listing == null) continue;
            boolean sold = item.status == ItemStatus.SOLD;
            if (sold) {
                soldCount++;
                sales += salesByListing.getOrDefault(listing.id, 0.0);
            }
            if (listing.active) activeCount++;
            sellerItems.add(map("itemId", item.id, "listingId", listing.id, "itemName", item.name,
                    "price", listing.price, "active", listing.active, "sold", sold, "photoUrl",
                    listing.photoData == null ? null : "/api/market/" + listing.id + "/photo"));
        }
        return map("buyer", map("orders", buyerCount, "totalSpent", spent, "recent", purchases),
                "seller", map("activeListings", activeCount, "soldItems", soldCount, "salesTotal", sales, "items", sellerItems));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static boolean in(LocalDate d, LocalDate from, LocalDate to) { return !d.isBefore(from) && !d.isAfter(to); }

    // ================================================================== admin
    public Map<String, Object> adminAnalytics(int daysParam, LocalDate today) {
        int days = Math.max(7, Math.min(180, daysParam));
        int bucket = days <= 31 ? 1 : days <= 90 ? 7 : 14;
        LocalDate from = today.minusDays(days - 1), to = today;
        LocalDate pFrom = from.minusDays(days), pTo = from.minusDays(1);
        int nb = (int) Math.ceil(days / (double) bucket);

        List<EWasteItem> items = repo.items();
        Map<Integer, EWasteItem> byId = new HashMap<>();
        for (EWasteItem i : items) byId.put(i.id, i);
        List<Order> orders = repo.orders();
        List<RecyclingBatch> batches = repo.batches();

        double[] submitted = new double[nb], kgIn = new double[nb], revenue = new double[nb], recycled = new double[nb];
        double[] cur = new double[5], prev = new double[5];   // submitted, kgIn, revenue, recycledKg, co2

        for (EWasteItem i : items) {
            LocalDate d = i.createdAt.toLocalDate();
            boolean ok = i.status != ItemStatus.REJECTED;
            if (in(d, from, to)) {
                int b = (int) (ChronoUnit.DAYS.between(from, d) / bucket);
                submitted[b]++;
                cur[0]++;
                if (ok) { kgIn[b] += i.weightKg; cur[1] += i.weightKg; }
            } else if (in(d, pFrom, pTo)) {
                prev[0]++;
                if (ok) prev[1] += i.weightKg;
            }
        }
        for (Order o : orders) {
            LocalDate d = o.time.toLocalDate();
            EWasteItem it = byId.get(o.itemId);
            double co2 = it == null ? 0 : it.weightKg * it.category.co2PerKg * 2;
            if (in(d, from, to)) { revenue[(int) (ChronoUnit.DAYS.between(from, d) / bucket)] += o.amountPaid; cur[2] += o.amountPaid; cur[4] += co2; }
            else if (in(d, pFrom, pTo)) { prev[2] += o.amountPaid; prev[4] += co2; }
        }
        double metal = 0, plastic = 0, glass = 0, hazard = 0, other = 0;
        for (RecyclingBatch b : batches) {
            LocalDate d = b.time.toLocalDate();
            if (in(d, from, to)) {
                recycled[(int) (ChronoUnit.DAYS.between(from, d) / bucket)] += b.totalKg;
                cur[3] += b.totalKg; cur[4] += b.co2SavedKg;
                metal += b.metalKg; plastic += b.plasticKg; glass += b.glassKg; hazard += b.hazardKg; other += b.otherKg;
            } else if (in(d, pFrom, pTo)) { prev[3] += b.totalKg; prev[4] += b.co2SavedKg; }
        }

        List<Object> labels = new ArrayList<>();
        for (int b = 0; b < nb; b++) labels.add(from.plusDays((long) b * bucket).toString());

        List<Object> kpis = new ArrayList<>();
        String[][] defs = {{"submitted", "Items submitted", "count"}, {"kgIn", "E-waste submitted", "kg"}, {"revenue", "Market revenue", "money"},
                {"recycledKg", "Recycled", "kg"}, {"co2", "CO2 avoided", "kg"}};
        for (int k = 0; k < defs.length; k++) kpis.add(map("key", defs[k][0], "label", defs[k][1], "unit", defs[k][2], "value", cur[k], "previous", prev[k]));

        // breakdowns over items submitted in the period
        Map<Category, double[]> cat = new EnumMap<>(Category.class);
        Map<String, Integer> outcome = new LinkedHashMap<>();
        for (Decision d : Decision.values()) outcome.put(d.name(), 0);
        int pending = 0, rejected = 0, approved = 0, collected = 0, processed = 0, total = 0;
        Map<Integer, Double> donorKg = new HashMap<>();
        for (EWasteItem i : items) {
            if (!in(i.createdAt.toLocalDate(), from, to)) continue;
            total++;
            double[] c = cat.computeIfAbsent(i.category, k -> new double[2]);
            c[0]++; c[1] += i.weightKg;
            if (i.status == ItemStatus.REJECTED) rejected++;
            else if (i.decision == null) pending++;
            else outcome.merge(i.decision.name(), 1, Integer::sum);
            if (i.status != ItemStatus.SUBMITTED && i.status != ItemStatus.REJECTED) approved++;
            if (Rules.received(i)) { collected++; donorKg.merge(i.ownerId, i.weightKg, Double::sum); }
            if (i.status == ItemStatus.SOLD || i.status == ItemStatus.RECYCLED) processed++;
        }
        List<Map<String, Object>> byCategory = new ArrayList<>();
        for (Map.Entry<Category, double[]> e : cat.entrySet())
            byCategory.add(map("name", e.getKey().name(), "label", e.getKey().label, "count", (int) e.getValue()[0], "kg", e.getValue()[1]));
        byCategory.sort((a, b) -> Double.compare((Double) b.get("kg"), (Double) a.get("kg")));

        List<Object> outcomes = new ArrayList<>();
        for (Decision d : Decision.values()) outcomes.add(map("name", d.name(), "label", shortLabel(d), "count", outcome.get(d.name())));
        outcomes.add(map("name", "PENDING", "label", "Awaiting decision", "count", pending));
        outcomes.add(map("name", "REJECTED", "label", "Rejected", "count", rejected));

        List<Object> funnel = new ArrayList<>();
        funnel.add(map("label", "Submitted", "count", total));
        funnel.add(map("label", "Approved", "count", approved));
        funnel.add(map("label", "Collected", "count", collected));
        funnel.add(map("label", "Reused / recycled", "count", processed));

        Map<Integer, String> names = new HashMap<>();
        for (User u : repo.users()) names.put(u.id, u.fullName);
        List<Map.Entry<Integer, Double>> donors = new ArrayList<>(donorKg.entrySet());
        donors.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Object> top = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : donors.subList(0, Math.min(5, donors.size())))
            top.add(map("label", names.getOrDefault(e.getKey(), "?"), "kg", e.getValue()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("bucketDays", bucket);
        out.put("from", from.toString());
        out.put("to", to.toString());
        out.put("kpis", kpis);
        out.put("labels", labels);
        out.put("submitted", asList(submitted));
        out.put("kgIn", asList(kgIn));
        out.put("revenue", asList(revenue));
        out.put("recycledKg", asList(recycled));
        out.put("byCategory", byCategory);
        out.put("outcomes", outcomes);
        out.put("funnel", funnel);
        out.put("materials", map("metal", metal, "plastic", plastic, "glass", glass, "hazardous", hazard, "other", other));
        out.put("topDonors", top);
        out.put("alerts", alerts(items, today));
        return out;
    }

    private static String shortLabel(Decision d) {
        switch (d) {
            case REUSE: return "Reuse";
            case REPAIR_REUSE: return "Repair & reuse";
            case RECYCLE: return "Recycle";
            default: return "Hazardous disposal";
        }
    }

    private static List<Object> asList(double[] a) {
        List<Object> l = new ArrayList<>();
        for (double v : a) l.add(v);
        return l;
    }

    /** "Needs attention" list for the admin: what is waiting, late or ready. */
    List<Object> alerts(List<EWasteItem> items, LocalDate today) {
        LocalDateTime twoDaysAgo = today.atStartOfDay().minusDays(2);
        int oldSubmissions = 0, overdue = 0, readyToList = 0, waitRepair = 0, unqueued = 0, hazCount = 0;
        double hazKg = 0, regKg = 0;
        for (EWasteItem i : items) {
            if (i.status == ItemStatus.SUBMITTED && i.createdAt.isBefore(twoDaysAgo)) oldSubmissions++;
            if (i.status == ItemStatus.PICKUP_SCHEDULED && i.pickupDate != null && i.pickupDate.isBefore(today)) overdue++;
            if (i.status == ItemStatus.COLLECTED && i.decision == Decision.REUSE) readyToList++;
            if (i.status == ItemStatus.COLLECTED && i.decision == Decision.REPAIR_REUSE) waitRepair++;
            if (i.status == ItemStatus.COLLECTED && i.decision != null && i.decision.isRecycling()) unqueued++;
            if (i.status == ItemStatus.QUEUED_RECYCLE) {
                if (i.decision == Decision.HAZARDOUS_DISPOSAL) { hazCount++; hazKg += i.weightKg; } else regKg += i.weightKg;
            }
        }
        List<Object> a = new ArrayList<>();
        if (AuthService.usesDefaultPassword(repo))
            a.add(alert("warn", "The default admin password is still in use - change it now under Profile", "Profile"));
        if (oldSubmissions > 0) a.add(alert("warn", oldSubmissions + " submission(s) have waited more than 2 days for review", "Review submissions"));
        if (overdue > 0) a.add(alert("warn", overdue + " scheduled pickup(s) are overdue", "Pickups & stock"));
        if (hazCount > 0) a.add(alert("warn", hazCount + " hazardous item(s) (" + String.format(Locale.ROOT, "%.1f", hazKg)
                + " kg) are queued - ship them to a certified partner", "Recycling"));
        if (readyToList > 0) a.add(alert("info", readyToList + " reusable item(s) are ready to publish to the market", "Pickups & stock"));
        if (waitRepair > 0) a.add(alert("info", waitRepair + " item(s) are waiting for repair", "Pickups & stock"));
        if (unqueued > 0) a.add(alert("info", unqueued + " collected item(s) can be queued for recycling", "Recycling"));
        if (regKg >= 50) a.add(alert("info", String.format(Locale.ROOT, "%.0f kg of regular e-waste is queued and ready to ship", regKg), "Recycling"));
        if (a.isEmpty()) a.add(alert("ok", "All caught up - nothing needs attention right now", ""));
        return a;
    }

    private static Map<String, Object> alert(String level, String text, String tab) { return map("level", level, "text", text, "tab", tab); }

    // ================================================================== user
    static double[] equivalents(double co2) {
        // trees: ~21 kg CO2 absorbed per tree per year; phone charge ~8 g CO2; car ~0.12 kg CO2 per km
        return new double[]{co2 / 21.0, co2 / 0.008, co2 / 0.12};
    }

    public Map<String, Object> userDashboard(User u, LocalDate today) {
        List<EWasteItem> mine = new ArrayList<>();
        for (EWasteItem i : repo.items()) if (i.ownerId == u.id) mine.add(i);

        double kg = 0, co2 = 0;
        int submitted = 0, scheduled = 0, collected = 0, inProgress = 0, completed = 0, rejected = 0, received = 0;
        boolean batteryRecycled = false, repaired = false, sold = false;
        Map<YearMonth, Double> monthly = new HashMap<>();
        for (EWasteItem i : mine) {
            switch (i.status) {
                case SUBMITTED: submitted++; break;
                case PICKUP_SCHEDULED: scheduled++; break;
                case COLLECTED: collected++; break;
                case REFURBISHING: case LISTED: case QUEUED_RECYCLE: inProgress++; break;
                case SOLD: case RECYCLED: completed++; break;
                default: rejected++;
            }
            if (Rules.received(i)) {
                kg += i.weightKg;
                received++;
                monthly.merge(YearMonth.from(i.createdAt), i.weightKg, Double::sum);
            }
            if (i.status == ItemStatus.RECYCLED) co2 += i.weightKg * i.category.co2PerKg;
            if (i.status == ItemStatus.SOLD) { co2 += i.weightKg * i.category.co2PerKg * 2; sold = true; }
            if (i.status == ItemStatus.RECYCLED && i.category.hazardous) batteryRecycled = true;
            if (i.repairCost > 0 && (i.status == ItemStatus.LISTED || i.status == ItemStatus.SOLD)) repaired = true;
        }

        // tier
        int t = 0;
        for (int k = 0; k < TIER_KG.length; k++) if (kg >= TIER_KG[k]) t = k;
        boolean max = t == TIER_KG.length - 1;
        double progress = max ? 1.0 : (kg - TIER_KG[t]) / (TIER_KG[t + 1] - TIER_KG[t]);
        Map<String, Object> tier = map("name", TIER_NAMES[t], "level", t + 1, "minKg", TIER_KG[t],
                "nextName", max ? "" : TIER_NAMES[t + 1], "nextKg", max ? 0.0 : TIER_KG[t + 1], "progress", progress,
                "kgToNext", max ? 0.0 : TIER_KG[t + 1] - kg);

        // monthly (last 6 months)
        List<Object> mLabels = new ArrayList<>(), mKg = new ArrayList<>();
        YearMonth cur = YearMonth.from(today);
        for (int k = 5; k >= 0; k--) {
            YearMonth ym = cur.minusMonths(k);
            mLabels.add(ym.toString());
            mKg.add(monthly.getOrDefault(ym, 0.0));
        }

        // badges
        boolean drive = false;
        for (Campaign c : repo.campaigns())
            for (EWasteItem i : mine)
                if (i.status != ItemStatus.REJECTED && !i.createdAt.toLocalDate().isBefore(c.startDate) && !i.createdAt.toLocalDate().isAfter(c.endDate)) drive = true;
        List<Object> badges = new ArrayList<>();
        badges.add(badge("First step", "Submit your first item", !mine.isEmpty()));
        badges.add(badge("Handed over", "Have an item collected", received > 0));
        badges.add(badge("Regular", "Have 5 items collected", received >= 5));
        badges.add(badge("Heavy lifter", "Hand in 20 kg of e-waste", kg >= 20));
        badges.add(badge("Battery hero", "Get a battery recycled safely", batteryRecycled));
        badges.add(badge("Second life", "An item you gave was bought and reused", sold));
        badges.add(badge("Repair champion", "An item you gave was repaired and put on sale", repaired));
        badges.add(badge("Drive participant", "Contribute during a collection drive", drive));

        double[] eq = equivalents(co2);
        List<Object> recent = new ArrayList<>();
        List<EWasteItem> sorted = new ArrayList<>(mine);
        sorted.sort((a, b) -> Integer.compare(b.id, a.id));
        for (EWasteItem i : sorted.subList(0, Math.min(5, sorted.size())))
            recent.add(map("id", i.id, "name", i.name, "statusLabel", i.status.label, "trackingCode", Tracking.code(i)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("points", u.points);
        out.put("pointsValue", u.points * com.ewaste.server.service.MarketService.POINT_VALUE);
        out.put("kgDonated", kg);
        out.put("co2Saved", co2);
        out.put("itemCount", mine.size());
        out.put("tier", tier);
        out.put("badges", badges);
        out.put("pipeline", map("submitted", submitted, "scheduled", scheduled, "collected", collected, "inProgress", inProgress,
                "completed", completed, "rejected", rejected));
        out.put("monthlyLabels", mLabels);
        out.put("monthlyKg", mKg);
        out.put("equivalents", map("trees", eq[0], "phoneCharges", eq[1], "carKm", eq[2]));
        out.put("campaigns", campaigns.list(true, today));
        out.put("recent", recent);
        return out;
    }

    private static Map<String, Object> badge(String label, String description, boolean earned) {
        return map("label", label, "description", description, "earned", earned);
    }

    // ================================================================== certificate
    public String certificateHtml(User u, LocalDate today) {
        Map<String, Object> d = userDashboard(u, today);
        Map<String, Object> tier = castMap(d.get("tier"));
        Map<String, Object> eq = castMap(d.get("equivalents"));
        double kg = (Double) d.get("kgDonated"), co2 = (Double) d.get("co2Saved");
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Impact certificate</title><style>"
                + "body{font-family:Georgia,serif;background:#f1f8e9;margin:0;padding:40px}"
                + ".c{max-width:760px;margin:auto;background:#fff;border:6px double #2e7d32;padding:48px;text-align:center}"
                + "h1{color:#1b5e20;margin:0 0 8px;font-size:34px}.s{color:#607d8b;margin-bottom:28px}"
                + ".n{font-size:30px;color:#2e7d32;margin:18px 0}.g{display:flex;gap:16px;justify-content:center;margin:28px 0}"
                + ".g div{flex:1;background:#e8f5e9;padding:16px;border-radius:6px}.g b{display:block;font-size:26px;color:#1b5e20}"
                + ".f{color:#607d8b;font-size:13px;margin-top:28px}</style></head><body><div class=\"c\">"
                + "<h1>Certificate of Responsible E-Waste Disposal</h1><div class=\"s\">E-Waste Manager</div>"
                + "<div>This certifies that</div><div class=\"n\">" + esc(u.fullName) + "</div>"
                + "<div>has handed in <b>" + String.format(Locale.ROOT, "%.1f", kg) + " kg</b> of electronic waste across "
                + received(d) + " item(s) for reuse and recycling, and holds the rank of <b>" + esc(String.valueOf(tier.get("name"))) + "</b>.</div>"
                + "<div class=\"g\"><div><b>" + String.format(Locale.ROOT, "%.1f kg", co2) + "</b>CO2 emissions avoided</div>"
                + "<div><b>" + String.format(Locale.ROOT, "%.1f", (Double) eq.get("trees")) + "</b>tree-years of CO2 absorption</div>"
                + "<div><b>" + String.format(Locale.ROOT, "%,.0f", (Double) eq.get("carKm")) + " km</b>of car driving avoided</div></div>"
                + "<div class=\"f\">Issued on " + today + ". Figures are estimates based on typical material and emission factors.</div>"
                + "</div></body></html>";
    }

    private static int received(Map<String, Object> d) {
        Map<String, Object> p = castMap(d.get("pipeline"));
        return ((Integer) p.get("collected")) + ((Integer) p.get("inProgress")) + ((Integer) p.get("completed"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) { return (Map<String, Object>) o; }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
