package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.util.*;

public class ReportService {
    private final Repository repo;

    public ReportService(Repository repo) { this.repo = repo; }

    private static boolean received(EWasteItem i) {
        return i.status != ItemStatus.REJECTED && i.status != ItemStatus.SUBMITTED && i.status != ItemStatus.PICKUP_SCHEDULED;
    }

    public Map<String, Object> stats() {
        List<EWasteItem> items = repo.items();
        Map<String, Object> m = new LinkedHashMap<>();
        int users = 0;
        for (User u : repo.users()) if (!u.isAdmin()) users++;
        double total = 0, reused = 0, reusedCo2 = 0;
        Map<String, Object> byStatus = new LinkedHashMap<>();
        for (ItemStatus s : ItemStatus.values()) byStatus.put(s.name(), 0);
        for (EWasteItem i : items) {
            byStatus.put(i.status.name(), (Integer) byStatus.get(i.status.name()) + 1);
            if (i.status != ItemStatus.REJECTED) total += i.weightKg;
            if (i.status == ItemStatus.SOLD) { reused += i.weightKg; reusedCo2 += i.weightKg * i.category.co2PerKg * 2; }
        }
        double recycled = 0, metal = 0, plastic = 0, glass = 0, hazard = 0, other = 0, co2 = reusedCo2;
        List<RecyclingBatch> batches = repo.batches();
        for (RecyclingBatch b : batches) {
            recycled += b.totalKg; metal += b.metalKg; plastic += b.plasticKg; glass += b.glassKg;
            hazard += b.hazardKg; other += b.otherKg; co2 += b.co2SavedKg;
        }
        double revenue = 0;
        for (Order o : repo.orders()) revenue += o.amountPaid;

        m.put("users", users);
        m.put("items", items.size());
        m.put("totalKg", total);
        m.put("reusedKg", reused);
        m.put("recycledKg", recycled);
        m.put("batches", batches.size());
        m.put("revenue", revenue);
        m.put("co2Saved", co2);
        m.put("diversionRate", total == 0 ? 0.0 : (reused + recycled) / total * 100.0);
        m.put("byStatus", byStatus);
        Map<String, Object> mats = new LinkedHashMap<>();
        mats.put("metal", metal); mats.put("plastic", plastic); mats.put("glass", glass);
        mats.put("hazardous", hazard); mats.put("other", other);
        m.put("materials", mats);
        return m;
    }

    public double kgDonatedBy(int userId) {
        double t = 0;
        for (EWasteItem i : repo.items()) if (i.ownerId == userId && received(i)) t += i.weightKg;
        return t;
    }

    public Map<String, Object> impact(User u) {
        double kg = 0, co2 = 0;
        for (EWasteItem i : repo.items()) {
            if (i.ownerId != u.id) continue;
            if (received(i)) kg += i.weightKg;
            if (i.status == ItemStatus.RECYCLED) co2 += i.weightKg * i.category.co2PerKg;
            if (i.status == ItemStatus.SOLD) co2 += i.weightKg * i.category.co2PerKg * 2;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", u.points);
        m.put("pointsValue", u.points * MarketService.POINT_VALUE);
        m.put("kgDonated", kg);
        m.put("co2Saved", co2);
        return m;
    }

    public List<Map<String, Object>> leaderboard(int top) {
        List<User> list = new ArrayList<>();
        for (User u : repo.users()) if (!u.isAdmin() && u.active) list.add(u);
        final Map<Integer, Double> kg = new HashMap<>();
        for (EWasteItem i : repo.items()) if (received(i)) kg.merge(i.ownerId, i.weightKg, Double::sum);
        list.sort((a, b) -> Double.compare(kg.getOrDefault(b.id, 0.0), kg.getOrDefault(a.id, 0.0)));
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (User u : list.subList(0, Math.min(top, list.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("username", u.username);
            m.put("fullName", u.fullName);
            m.put("kgDonated", kg.getOrDefault(u.id, 0.0));
            m.put("points", u.points);
            out.add(m);
        }
        return out;
    }

    public String exportCsv() {
        StringBuilder sb = new StringBuilder("id,owner,name,brand,category,ageYears,weightKg,condition,status,decision,estimatedValue,pointsAwarded\n");
        Map<Integer, String> names = new HashMap<>();
        for (User u : repo.users()) names.put(u.id, u.username);
        for (EWasteItem i : repo.items()) {
            sb.append(i.id).append(',').append(csv(names.getOrDefault(i.ownerId, "?"))).append(',').append(csv(i.name)).append(',')
              .append(csv(i.brand)).append(',').append(i.category.name()).append(',').append(i.ageYears).append(',')
              .append(String.format(Locale.ROOT, "%.2f", i.weightKg)).append(',').append(i.condition.name()).append(',')
              .append(i.status.name()).append(',').append(i.decision == null ? "" : i.decision.name()).append(',')
              .append(String.format(Locale.ROOT, "%.2f", i.estimatedValue)).append(',').append(i.pointsAwarded).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
