package com.ewaste;

import com.ewaste.client.*;
import com.ewaste.server.ServerMain;
import com.ewaste.server.repo.MemoryRepository;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ewaste.client.ApiClient.map;
import static com.ewaste.client.ApiClient.obj;

/** Opens the real Swing windows against a real server (needs a display; use xvfb-run on a headless box). */
public class GuiSmoke {
    static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "screenshots");
        outDir.mkdirs();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> problems.add("Uncaught on " + t.getName() + ": " + e));
        MemoryRepository repo = new MemoryRepository(null);
        HttpServer server = ServerMain.start(repo, "127.0.0.1", 0);
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        seed(url, repo);
        SwingUtilities.invokeAndWait(Ui::installLookAndFeel);

        capture(url, "admin", "admin123", outDir);
        capture(url, "alice", "secret1", outDir);
        capture(url, "bob", "secret2", outDir);

        // login window
        final LoginFrame[] lf = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> { lf[0] = new LoginFrame(url); lf[0].setVisible(true); });
        Thread.sleep(600);
        shot(lf[0], new File(outDir, "login.png"));
        SwingUtilities.invokeAndWait(lf[0]::dispose);

        server.stop(0);
        System.out.println(problems.isEmpty() ? "GUI smoke test: no errors" : "GUI problems:\n  " + String.join("\n  ", problems));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    static void seed(String url, MemoryRepository repo) {
        ApiClient anon = new ApiClient(url), admin = new ApiClient(url), alice = new ApiClient(url), bob = new ApiClient(url);
        anon.post("/api/auth/register", map("username", "alice", "password", "secret1", "fullName", "Alice Johnson", "email", "alice@example.com", "phone", "555-0101", "address", "12 Green St"));
        anon.post("/api/auth/register", map("username", "bob", "password", "secret2", "fullName", "Bob Martin", "email", "bob@example.com"));
        admin.login("admin", "admin123"); alice.login("alice", "secret1"); bob.login("bob", "secret2");
        String today = LocalDate.now().toString();
        int[] ids = new int[6];
        Object[][] items = {
            {"Pixel 4", "Google", "MOBILE", 2, 0.2, "WORKING"}, {"ThinkPad T480", "Lenovo", "LAPTOP", 3, 2.0, "MINOR_FAULT"},
            {"Old CRT TV", "Sony", "TELEVISION", 15, 20.0, "DEAD"}, {"Swollen battery", "", "BATTERY", 4, 0.5, "DEAD"},
            {"iPad Air", "Apple", "TABLET", 3, 0.5, "WORKING"}, {"Inkjet printer", "HP", "PRINTER", 5, 5.0, "MAJOR_FAULT"}};
        for (int k = 0; k < items.length; k++) {
            ApiClient owner = k % 2 == 0 ? alice : bob;
            Object[] it = items[k];
            ids[k] = ((Number) obj(owner.post("/api/items", map("name", it[0], "brand", it[1], "category", it[2], "ageYears", it[3],
                    "weightKg", it[4], "condition", it[5], "description", "Seeded demo item"))).get("id")).intValue();
        }
        for (int k = 0; k < 4; k++) {
            Map<String, Object> m = obj(admin.get("/api/items/" + ids[k]));
            admin.post("/api/admin/items/" + ids[k] + "/approve", map("decision", m.get("suggestedDecision"), "pickupDate", today, "note", "Please keep the charger"));
            admin.post("/api/admin/items/" + ids[k] + "/collect", null);
        }
        admin.post("/api/admin/listings", map("itemId", ids[0], "price", 95));
        admin.post("/api/admin/items/" + ids[1] + "/refurbish/start", map("repairCost", 40));
        admin.post("/api/admin/recycling/queue-all", null);
        admin.post("/api/admin/recycling/batches", map("partner", "GreenCycle Ltd", "hazardous", false));
        admin.post("/api/admin/announce", map("message", "Free collection drive this Saturday!"));
        admin.post("/api/admin/items/" + ids[5] + "/reject", map("reason", "Missing cartridge - not accepted"));
        bob.post("/api/market/" + obj(((java.util.List<?>) bob.get("/api/market")).get(0)).get("id") + "/buy", map("points", 0));

        // more history so the charts have something to show: spread items over the last two months
        int[] ago = {0, 2, 6, 14, 30, 45};
        for (int k = 0; k < ids.length; k++) {
            com.ewaste.model.EWasteItem it = repo.item(ids[k]);
            it.createdAt = it.createdAt.minusDays(ago[k]);
            repo.saveItem(it);
        }
        for (int k = 0; k < 12; k++) {
            ApiClient owner = k % 3 == 0 ? bob : alice;
            int id = ((Number) obj(owner.post("/api/items", map("name", "Old device " + k, "category", k % 2 == 0 ? "MOBILE" : "LAPTOP", "ageYears", 4,
                    "weightKg", 0.4 + (k % 5), "condition", k % 4 == 0 ? "WORKING" : "DEAD"))).get("id")).intValue();
            Map<String, Object> m = obj(admin.get("/api/items/" + id));
            if (k % 3 != 0) { admin.post("/api/admin/items/" + id + "/approve", map("decision", m.get("suggestedDecision"), "pickupDate", today, "note", "")); if (k % 3 == 1) admin.post("/api/admin/items/" + id + "/collect", null); }
            com.ewaste.model.EWasteItem it = repo.item(id);
            it.createdAt = it.createdAt.minusDays(1 + k * 5 % 55);
            repo.saveItem(it);
        }
        admin.post("/api/admin/campaigns", map("name", "Spring e-waste drive", "description", "Clear the drawers", "goalKg", 40, "endDate", LocalDate.now().plusDays(20).toString()));
    }

    static void capture(String url, String user, String pw, File outDir) throws Exception {
        ApiClient api = new ApiClient(url);
        App app = new App(api);
        app.user = api.login(user, pw);
        app.loadMeta();
        final MainFrame[] f = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            f[0] = app.isAdmin() ? AdminFrame.create(app, () -> { }) : UserFrame.create(app, () -> { });
            f[0].setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f[0].setVisible(true);
        });
        JTabbedPane tabs = find(f[0].getContentPane(), JTabbedPane.class);
        int n = tabs.getTabCount();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            boolean dash = i == 0;   // dashboards are tall: render them at full height
            SwingUtilities.invokeAndWait(() -> { f[0].setSize(1100, dash ? 1500 : 700); tabs.setSelectedIndex(idx); });
            Thread.sleep(1200);
            checkDialogs(user + " tab " + tabs.getTitleAt(i));
            String name = user + "-" + (i + 1) + "-" + tabs.getTitleAt(i).toLowerCase().replaceAll("[^a-z]+", "-") + ".png";
            shot(f[0], new File(outDir, name));
        }
        SwingUtilities.invokeAndWait(f[0]::dispose);
        System.out.println(user + ": " + n + " tabs rendered");
    }

    static void checkDialogs(String where) throws Exception {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isVisible()) {
                problems.add("Dialog open on " + where + ": " + ((JDialog) w).getTitle());
                SwingUtilities.invokeAndWait(w::dispose);
            }
        }
    }

    static <T extends Component> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return type.cast(k);
            if (k instanceof Container) { T r = find((Container) k, type); if (r != null) return r; }
        }
        return null;
    }

    static void shot(Window w, File file) throws Exception {
        BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> { Graphics g = img.getGraphics(); w.paint(g); g.dispose(); });
        ImageIO.write(img, "png", file);
    }
}
