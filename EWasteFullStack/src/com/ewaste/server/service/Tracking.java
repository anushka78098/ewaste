package com.ewaste.server.service;

import com.ewaste.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracking codes (EW-2026-00123) and the step-by-step progress shown by the public tracker. */
public final class Tracking {
    private static final Pattern CODE = Pattern.compile("^EW-(\\d{4})-(\\d{5,})$", Pattern.CASE_INSENSITIVE);

    private Tracking() {}

    public static String code(EWasteItem i) { return String.format("EW-%d-%05d", i.createdAt.getYear(), i.id); }

    /** @return the item id encoded in the code, or -1 if the text is not a tracking code */
    public static int parse(String code) {
        if (code == null) return -1;
        Matcher m = CODE.matcher(code.trim());
        if (!m.matches()) return -1;
        try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { return -1; }
    }

    private static Map<String, Object> step(String label, boolean done) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("done", done);
        return m;
    }

    public static List<Object> steps(EWasteItem i) {
        List<Object> s = new ArrayList<>();
        ItemStatus st = i.status;
        s.add(step("Submitted", true));
        if (st == ItemStatus.REJECTED) { s.add(step("Rejected by the admin", true)); return s; }
        boolean collected = st == ItemStatus.COLLECTED || st == ItemStatus.REFURBISHING || st == ItemStatus.LISTED
                || st == ItemStatus.SOLD || st == ItemStatus.QUEUED_RECYCLE || st == ItemStatus.RECYCLED;
        s.add(step("Approved, pickup scheduled", st != ItemStatus.SUBMITTED));
        s.add(step("Collected at the facility", collected));
        boolean reusePath = i.decision == Decision.REUSE || i.decision == Decision.REPAIR_REUSE;
        boolean recyclePath = i.decision != null && i.decision.isRecycling();
        if (i.decision == Decision.REPAIR_REUSE || i.repairCost > 0) {
            boolean refurbished = i.repairCost > 0 && (st == ItemStatus.COLLECTED && i.decision == Decision.REUSE || st == ItemStatus.LISTED || st == ItemStatus.SOLD);
            s.add(step("Repaired and refurbished", refurbished));
        }
        if (reusePath) {
            s.add(step("Listed in the market", st == ItemStatus.LISTED || st == ItemStatus.SOLD));
            s.add(step("Reused by a new owner", st == ItemStatus.SOLD));
        } else if (recyclePath) {
            s.add(step("Queued for recycling", st == ItemStatus.QUEUED_RECYCLE || st == ItemStatus.RECYCLED));
            s.add(step("Recycled" + (i.decision == Decision.HAZARDOUS_DISPOSAL ? " at a certified facility" : ""), st == ItemStatus.RECYCLED));
        } else {
            s.add(step("Reused or recycled (decided at review)", false));
        }
        return s;
    }
}
