package com.ewaste.server.service;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Collection drives: the admin sets a weight goal and an end date, everyone sees the progress. */
public class CampaignService {
    private final Repository repo;
    private final NotificationService notes;

    public CampaignService(Repository repo, NotificationService notes) {
        this.repo = repo;
        this.notes = notes;
    }

    public Campaign create(String name, String description, double goalKg, LocalDate end, LocalDate today) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("A campaign name is required.");
        if (goalKg < 1 || goalKg > 100_000) throw new IllegalArgumentException("The goal must be between 1 and 100000 kg.");
        if (end == null || end.isBefore(today)) throw new IllegalArgumentException("The end date must be today or later.");
        if (end.isAfter(today.plusDays(365))) throw new IllegalArgumentException("A campaign can run for at most one year.");
        Campaign c = new Campaign();
        c.name = name.trim();
        c.description = description == null ? "" : description.trim();
        c.goalKg = goalKg;
        c.startDate = today;
        c.endDate = end;
        repo.addCampaign(c);
        notes.announce("New collection drive: " + c.name + " - goal " + fmt(goalKg) + " kg by " + end
                + (c.description.isEmpty() ? "." : ". " + c.description));
        return c;
    }

    public void end(int id) {
        Campaign c = repo.campaign(id);
        if (c == null) throw new IllegalArgumentException("No campaign with id " + id + ".");
        c.active = false;
        repo.saveCampaign(c);
    }

    private static String fmt(double kg) { return kg == Math.rint(kg) ? String.valueOf((long) kg) : String.format(Locale.ROOT, "%.1f", kg); }

    /** Campaign with live progress: weight of non-rejected items submitted during the campaign. */
    public Map<String, Object> view(Campaign c, List<EWasteItem> items, LocalDate today) {
        double kg = 0;
        Set<Integer> owners = new HashSet<>();
        for (EWasteItem i : items) {
            if (i.status == ItemStatus.REJECTED) continue;
            LocalDate d = i.createdAt.toLocalDate();
            if (d.isBefore(c.startDate) || d.isAfter(c.endDate)) continue;
            kg += i.weightKg;
            owners.add(i.ownerId);
        }
        String status = !c.active ? "ENDED" : c.endDate.isBefore(today) ? "FINISHED" : "ACTIVE";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("name", c.name);
        m.put("description", c.description);
        m.put("goalKg", c.goalKg);
        m.put("startDate", c.startDate.toString());
        m.put("endDate", c.endDate.toString());
        m.put("status", status);
        m.put("progressKg", kg);
        m.put("percent", Math.min(100.0, kg / c.goalKg * 100.0));
        m.put("goalReached", kg >= c.goalKg);
        m.put("contributors", owners.size());
        m.put("daysLeft", status.equals("ACTIVE") ? ChronoUnit.DAYS.between(today, c.endDate) : 0L);
        return m;
    }

    public List<Object> list(boolean activeOnly, LocalDate today) {
        List<EWasteItem> items = repo.items();
        List<Object> out = new ArrayList<>();
        List<Campaign> all = repo.campaigns();
        for (int k = all.size() - 1; k >= 0; k--) {
            Map<String, Object> v = view(all.get(k), items, today);
            if (!activeOnly || "ACTIVE".equals(v.get("status"))) out.add(v);
        }
        return out;
    }
}
