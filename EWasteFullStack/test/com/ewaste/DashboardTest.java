package com.ewaste;

import com.ewaste.client.ApiClient;
import com.ewaste.client.ApiClient.ApiException;
import com.ewaste.model.EWasteItem;
import com.ewaste.server.ServerMain;
import com.ewaste.server.repo.MemoryRepository;
import com.sun.net.httpserver.HttpServer;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.ewaste.client.ApiClient.*;

/** Dashboards, tracking codes, campaigns and the certificate. Item dates are shifted into the past to test periods. */
public class DashboardTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  PASS " + name); } else { failed++; System.out.println("  FAIL " + name); }
    }

    static int statusOf(Callable<?> c) {
        try { c.call(); return 200; } catch (ApiException e) { return e.status; } catch (Exception e) { return -1; }
    }

    static double d(Object o) { return ((Number) o).doubleValue(); }
    static int n(Object o) { return ((Number) o).intValue(); }

    static void backdate(MemoryRepository repo, int itemId, int daysAgo) {
        EWasteItem i = repo.item(itemId);
        i.createdAt = i.createdAt.minusDays(daysAgo);
        repo.saveItem(i);
    }

    static int submit(ApiClient who, String name, String cat, int age, double kg, String cond) {
        return n(obj(who.post("/api/items", map("name", name, "category", cat, "ageYears", age, "weightKg", kg, "condition", cond))).get("id"));
    }

    static void collect(ApiClient admin, int id, String decision) {
        admin.post("/api/admin/items/" + id + "/approve", map("decision", decision, "pickupDate", LocalDate.now().toString()));
        admin.post("/api/admin/items/" + id + "/collect", null);
    }

    public static void main(String[] args) throws Exception {
        MemoryRepository repo = new MemoryRepository(null);
        HttpServer server = ServerMain.start(repo, "127.0.0.1", 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try { run(url, repo); } finally { server.stop(0); }
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    @SuppressWarnings("unchecked")
    static void run(String url, MemoryRepository repo) {
        ApiClient anon = new ApiClient(url), admin = new ApiClient(url), alice = new ApiClient(url), bob = new ApiClient(url), eve = new ApiClient(url);
        admin.login("admin", "admin123");

        System.out.println("Empty system");
        Map<String, Object> empty = obj(admin.get("/api/admin/analytics"));
        check("fresh install warns about the default admin password", list(empty.get("alerts")).size() == 1
                && "warn".equals(list(empty.get("alerts")).get(0).get("level")) && list(empty.get("alerts")).get(0).get("text").toString().contains("default admin password"));
        admin.post("/api/me/password", map("oldPassword", "admin123", "newPassword", "a-better-one"));
        Map<String, Object> clean = obj(admin.get("/api/admin/analytics"));
        check("after changing it: only the 'all caught up' alert", list(clean.get("alerts")).size() == 1 && "ok".equals(list(clean.get("alerts")).get(0).get("level")));
        check("30-day view has 30 daily buckets", ((List<?>) empty.get("labels")).size() == 30 && n(empty.get("bucketDays")) == 1);

        anon.post("/api/auth/register", map("username", "alice", "password", "secret1", "fullName", "Alice A", "email", "a@x.com"));
        anon.post("/api/auth/register", map("username", "bob", "password", "secret2", "fullName", "Bob B", "email", "b@x.com"));
        anon.post("/api/auth/register", map("username", "eve", "password", "secret3", "fullName", "<b>Eve</b> & Co", "email", "e@x.com"));
        alice.login("alice", "secret1"); bob.login("bob", "secret2"); eve.login("eve", "secret3");

        System.out.println("Build history");
        int phone = submit(alice, "Pixel 4", "MOBILE", 2, 0.2, "WORKING");
        int laptop = submit(alice, "ThinkPad", "LAPTOP", 3, 2.0, "MINOR_FAULT");
        int tv = submit(alice, "Old CRT", "TELEVISION", 15, 20, "DEAD");
        int battery = submit(alice, "Swollen battery", "BATTERY", 4, 0.5, "DEAD");
        int tablet = submit(bob, "iPad", "TABLET", 3, 0.5, "WORKING");
        int pending = submit(bob, "Printer", "PRINTER", 5, 5, "MAJOR_FAULT");
        collect(admin, phone, "REUSE");
        collect(admin, laptop, "REPAIR_REUSE");
        collect(admin, tv, "RECYCLE");
        collect(admin, battery, "HAZARDOUS_DISPOSAL");
        admin.post("/api/admin/items/" + tablet + "/approve", map("decision", "REUSE", "pickupDate", LocalDate.now().toString()));
        int listing = n(obj(admin.post("/api/admin/listings", map("itemId", phone, "price", 100))).get("id"));
        bob.post("/api/market/" + listing + "/buy", map("points", 0));
        admin.post("/api/admin/recycling/queue-all", null);
        admin.post("/api/admin/recycling/batches", map("partner", "GreenCycle", "hazardous", false));
        // shift creation dates: phone 3d, laptop 10d, battery 5d ago (current 30-day window); tv 40d ago (previous window); printer 3d (waiting for review)
        backdate(repo, phone, 3); backdate(repo, laptop, 10); backdate(repo, battery, 5); backdate(repo, tv, 40); backdate(repo, pending, 3);

        System.out.println("Admin analytics");
        Map<String, Object> a = obj(admin.get("/api/admin/analytics?days=30"));
        Map<String, Object> submitted = list(a.get("kpis")).get(0), kgIn = list(a.get("kpis")).get(1), revenue = list(a.get("kpis")).get(2), recycled = list(a.get("kpis")).get(3);
        check("submitted this period: today's tablet + phone, laptop, battery, printer = 5", d(submitted.get("value")) == 5);
        check("previous period holds the 40-day-old TV", d(submitted.get("previous")) == 1);
        check("kg submitted = phone 0.2 + laptop 2 + battery 0.5 + tablet 0.5 + printer 5", Math.abs(d(kgIn.get("value")) - 8.2) < 1e-9);
        check("revenue is today's sale", d(revenue.get("value")) == 100.0);
        check("recycled kg this period is the regular batch (TV, 40 days old but shipped today)", d(recycled.get("value")) == 20.0);
        List<Object> sub = (List<Object>) a.get("submitted");
        double sum = 0;
        for (Object o : sub) sum += d(o);
        check("daily series sums to the KPI", sum == 5.0 && sub.size() == 30);
        check("backdated items land in the right daily buckets (today=29, 3d ago=26 has 2, 5d ago=24, 10d ago=19)",
                d(sub.get(29)) == 1 && d(sub.get(26)) == 2 && d(sub.get(24)) == 1 && d(sub.get(19)) == 1);
        check("category breakdown sorted by weight", ((List<?>) a.get("byCategory")).size() >= 4 && d(list(a.get("byCategory")).get(0).get("kg")) >= d(list(a.get("byCategory")).get(1).get("kg")));
        List<Map<String, Object>> funnel = list(a.get("funnel"));
        check("funnel never grows downstream", n(funnel.get(0).get("count")) >= n(funnel.get(1).get("count")) && n(funnel.get(1).get("count")) >= n(funnel.get(2).get("count"))
                && n(funnel.get(2).get("count")) >= n(funnel.get(3).get("count")));
        check("outcome list covers every decision plus pending/rejected", list(a.get("outcomes")).size() == 6);
        check("top donors lists alice first", "Alice A".equals(list(a.get("topDonors")).get(0).get("label")));
        Map<String, Object> mats = obj(a.get("materials"));
        check("materials from batches in period", Math.abs(d(mats.get("metal")) - 5.0) < 1e-9);
        check("90 days -> weekly buckets", ((List<?>) obj(admin.get("/api/admin/analytics?days=90")).get("labels")).size() == 13 && n(obj(admin.get("/api/admin/analytics?days=90")).get("bucketDays")) == 7);
        check("days is clamped to 7..180", n(obj(admin.get("/api/admin/analytics?days=1")).get("days")) == 7 && n(obj(admin.get("/api/admin/analytics?days=9999")).get("days")) == 180);
        check("bad days -> 400", statusOf(() -> admin.get("/api/admin/analytics?days=abc")) == 400);
        check("users cannot read analytics", statusOf(() -> alice.get("/api/admin/analytics")) == 403);

        System.out.println("Alerts");
        String alerts = a.get("alerts").toString();
        check("old unreviewed submission flagged", alerts.contains("waited more than 2 days"));
        check("ready-to-publish item flagged (tablet is approved only, laptop awaits repair)", alerts.contains("waiting for repair"));
        EWasteItem t = repo.item(tablet);
        t.pickupDate = LocalDate.now().minusDays(1);
        repo.saveItem(t);
        check("overdue pickup flagged", obj(admin.get("/api/admin/analytics")).get("alerts").toString().contains("overdue"));
        int bat2 = submit(alice, "Power bank", "BATTERY", 5, 1.0, "DEAD");
        collect(admin, bat2, "HAZARDOUS_DISPOSAL");
        admin.post("/api/admin/recycling/queue-all", null);
        check("queued hazardous item flagged", obj(admin.get("/api/admin/analytics")).get("alerts").toString().contains("certified partner"));

        admin.post("/api/admin/recycling/batches", map("partner", "SafeBatt Certified", "hazardous", true));   // ships both batteries
        System.out.println("User dashboard");
        Map<String, Object> ud = obj(alice.get("/api/me/dashboard"));
        check("tier from kg handed in (>= 5 kg -> Bronze)", "Bronze Recycler".equals(obj(ud.get("tier")).get("name")) && n(obj(ud.get("tier")).get("level")) == 2);
        check("progress towards the next tier", d(obj(ud.get("tier")).get("progress")) > 0 && d(obj(ud.get("tier")).get("progress")) < 1 && d(obj(ud.get("tier")).get("kgToNext")) > 0);
        List<Map<String, Object>> badges = list(ud.get("badges"));
        check("8 badges", badges.size() == 8);
        check("earned: first step, handed over, regular (5 collected), heavy lifter (23.7 kg), battery hero, second life",
                earned(badges, "First step") && earned(badges, "Handed over") && earned(badges, "Regular") && earned(badges, "Heavy lifter")
                && earned(badges, "Battery hero") && earned(badges, "Second life"));
        check("not earned yet: repair champion, drive participant", !earned(badges, "Repair champion") && !earned(badges, "Drive participant"));
        Map<String, Object> pipe = obj(ud.get("pipeline"));
        check("pipeline counts add up to all items", n(pipe.get("submitted")) + n(pipe.get("scheduled")) + n(pipe.get("collected")) + n(pipe.get("inProgress"))
                + n(pipe.get("completed")) + n(pipe.get("rejected")) == n(ud.get("itemCount")));
        check("6 monthly points", ((List<?>) ud.get("monthlyKg")).size() == 6);
        check("CO2 equivalents are positive", d(obj(ud.get("equivalents")).get("trees")) > 0 && d(obj(ud.get("equivalents")).get("carKm")) > 0);
        check("recent items carry tracking codes", list(ud.get("recent")).get(0).get("trackingCode").toString().startsWith("EW-"));
        check("another user's dashboard is independent", "Newcomer".equals(obj(obj(eve.get("/api/me/dashboard")).get("tier")).get("name")));

        System.out.println("Tracking");
        String code = obj(alice.get("/api/items/" + phone)).get("trackingCode").toString();
        check("code format EW-yyyy-nnnnn", code.matches("EW-\\d{4}-\\d{5}"));
        Map<String, Object> tr = obj(anon.get("/api/track/" + code));
        check("public lookup needs no login", "SOLD".equals(tr.get("status")));
        List<Map<String, Object>> steps = list(tr.get("steps"));
        boolean allDone = true;
        for (Map<String, Object> s : steps) allDone &= Boolean.TRUE.equals(s.get("done"));
        check("sold item: every step done", allDone && steps.size() == 5);
        check("lookup is case-insensitive", "SOLD".equals(obj(anon.get("/api/track/" + code.toLowerCase())).get("status")));
        check("public view leaks no personal data", !tr.toString().contains("Alice") && !tr.toString().contains("alice") && !tr.toString().contains("Pixel"));
        check("unknown code -> 404", statusOf(() -> anon.get("/api/track/EW-2026-99999")) == 404);
        check("malformed code -> 404", statusOf(() -> anon.get("/api/track/hello")) == 404);
        check("wrong year in code -> 404", statusOf(() -> anon.get("/api/track/" + code.replace("-" + LocalDate.now().getYear() + "-", "-1999-"))) == 404);
        List<Map<String, Object>> tsteps = list(obj(anon.get("/api/track/" + obj(admin.get("/api/items/" + tablet)).get("trackingCode"))).get("steps"));
        check("pending item: only early steps done", Boolean.TRUE.equals(tsteps.get(1).get("done")) && Boolean.FALSE.equals(tsteps.get(2).get("done")));
        check("admin can search by tracking code", list(admin.get("/api/admin/items?q=" + enc(code))).size() == 1);
        check("rejected item shows a short path", list(obj(anon.get("/api/track/" + rejectedCode(admin, bob))).get("steps")).size() == 2);

        System.out.println("Campaigns");
        check("bad goal -> 400", statusOf(() -> admin.post("/api/admin/campaigns", map("name", "x", "goalKg", 0, "endDate", LocalDate.now().plusDays(5).toString()))) == 400);
        check("past end date -> 400", statusOf(() -> admin.post("/api/admin/campaigns", map("name", "x", "goalKg", 10, "endDate", LocalDate.now().minusDays(1).toString()))) == 400);
        check("users cannot create campaigns", statusOf(() -> alice.post("/api/admin/campaigns", map("name", "x", "goalKg", 10, "endDate", LocalDate.now().plusDays(5).toString()))) == 403);
        Map<String, Object> camp = obj(admin.post("/api/admin/campaigns", map("name", "Spring Clean", "description", "Bring old chargers!", "goalKg", 10, "endDate", LocalDate.now().plusDays(7).toString())));
        check("new drive counts today's non-rejected items (tablet 0.5 + power bank 1.0); backdated and rejected ones do not",
                Math.abs(d(camp.get("progressKg")) - 1.5) < 1e-9 && "ACTIVE".equals(camp.get("status")));
        check("users are notified of the drive", alice.get("/api/notifications").toString().contains("Spring Clean"));
        int e1 = submit(eve, "Old phone", "MOBILE", 8, 2.5, "DEAD");
        int e2 = submit(alice, "Router", "OTHER", 3, 1.5, "WORKING");
        List<Map<String, Object>> active = list(bob.get("/api/campaigns"));
        check("users see the active drive", active.size() == 1 && "Spring Clean".equals(active.get(0).get("name")));
        double prog = d(active.get(0).get("progressKg"));
        check("progress 5.5 kg from 3 contributors, 7 days left", Math.abs(prog - 5.5) < 1e-9 && n(active.get(0).get("contributors")) == 3 && n(active.get(0).get("daysLeft")) == 7);
        check("percent = 55", Math.abs(d(active.get(0).get("percent")) - 55.0) < 1e-9);
        check("drive badge earned by a contributor", earned(list(obj(eve.get("/api/me/dashboard")).get("badges")), "Drive participant"));
        admin.post("/api/admin/campaigns/" + camp.get("id") + "/end", null);
        check("ended drive disappears for users but stays for the admin", list(bob.get("/api/campaigns")).isEmpty()
                && "ENDED".equals(list(admin.get("/api/admin/campaigns")).get(0).get("status")));

        System.out.println("Certificate");
        String html = eve.getText("/api/me/certificate");
        check("certificate is a complete HTML page", html.startsWith("<!doctype html>") && html.contains("Certificate of Responsible E-Waste Disposal"));
        check("name is HTML-escaped", html.contains("&lt;b&gt;Eve&lt;/b&gt; &amp; Co") && !html.contains("<b>Eve</b>"));
        check("certificate needs login", statusOf(() -> anon.getText("/api/me/certificate")) == 401);
    }

    static boolean earned(List<Map<String, Object>> badges, String label) {
        for (Map<String, Object> b : badges) if (label.equals(b.get("label"))) return Boolean.TRUE.equals(b.get("earned"));
        return false;
    }

    static String rejectedCode(ApiClient admin, ApiClient bob) {
        int id = submit(bob, "Junk", "OTHER", 1, 1, "WORKING");
        admin.post("/api/admin/items/" + id + "/reject", map("reason", "No"));
        return obj(admin.get("/api/items/" + id)).get("trackingCode").toString();
    }
}
