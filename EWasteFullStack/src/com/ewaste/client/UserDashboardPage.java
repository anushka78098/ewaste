package com.ewaste.client;

import com.ewaste.client.charts.*;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** A donor's own dashboard: reward tier, badges, progress, impact and active collection drives. */
public class UserDashboardPage extends Page {
    private final KpiCard kKg = new KpiCard("handed in", 0), kPoints = new KpiCard("reward points", 1),
            kCo2 = new KpiCard("CO2 avoided", 2), kItems = new KpiCard("items submitted", 3);
    private final JLabel tierName = new JLabel(" "), tierNote = new JLabel(" ");
    private final Meter tierMeter = new Meter();
    private final JPanel badges = new JPanel(new GridLayout(0, 4, 8, 8));
    private final DonutChart pipeline = new DonutChart("Where my items are");
    private final BarChart monthly = new BarChart("Handed in per month (kg)");
    private final JLabel equiv = new JLabel(" ");
    private final JPanel drives = new JPanel();
    private final MapTable recent = new MapTable(key("Tracking code", "trackingCode"), key("Item", "name"), key("Status", "statusLabel"));

    public UserDashboardPage(App app) {
        super(app, "My dashboard");
        pipeline.setCenterCaption("items");
        monthly.setFormat(v -> String.format("%.1f kg", v));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 10, 0));
        kpis.add(kKg); kpis.add(kPoints); kpis.add(kCo2); kpis.add(kItems);

        tierName.setFont(tierName.getFont().deriveFont(Font.BOLD, 16f));
        tierName.setForeground(Ui.GREEN_DARK);
        tierNote.setForeground(Ui.GREY_TEXT);
        JPanel tier = new JPanel(new BorderLayout(0, 6));
        tier.setBorder(BorderFactory.createTitledBorder("Reward tier"));
        tier.add(tierName, BorderLayout.NORTH);
        tier.add(tierMeter, BorderLayout.CENTER);
        tier.add(tierNote, BorderLayout.SOUTH);

        JPanel badgeBox = new JPanel(new BorderLayout());
        badgeBox.setBorder(BorderFactory.createTitledBorder("Badges"));
        badgeBox.add(badges, BorderLayout.CENTER);

        JPanel charts = new JPanel(new GridLayout(1, 2, 10, 0));
        charts.add(pipeline);
        charts.add(monthly);
        charts.setPreferredSize(new Dimension(100, 250));

        equiv.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        drives.setLayout(new BoxLayout(drives, BoxLayout.Y_AXIS));
        drives.setBorder(BorderFactory.createTitledBorder("Collection drives"));
        JPanel recentBox = new JPanel(new BorderLayout());
        recentBox.setBorder(BorderFactory.createTitledBorder("Recent items (share the tracking code to let anyone follow the item)"));
        recentBox.add(recent.scroll(), BorderLayout.CENTER);
        recentBox.setPreferredSize(new Dimension(100, 170));

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{kpis, tier, badgeBox, charts, equiv, drives, recentBox}) {
            c.setAlignmentX(0f);
            col.add(c);
            col.add(Box.createVerticalStrut(8));
        }
        JPanel wrap = new ScrollPanel(new BorderLayout());
        wrap.add(col, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(top("My dashboard", Ui.button("Save impact certificate...", this::saveCertificate), Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/me/dashboard")), this::show);
    }

    private void show(Map<String, Object> d) {
        kKg.set(Ui.kg(d.get("kgDonated")));
        kPoints.set(Ui.i(d.get("points")) + "  (" + Ui.money(d.get("pointsValue")) + ")");
        kCo2.set(Ui.kg(d.get("co2Saved")));
        kItems.set(String.valueOf(Ui.i(d.get("itemCount"))));

        Map<String, Object> t = ApiClient.obj(d.get("tier"));
        tierName.setText(Ui.s(t.get("name")) + "  (level " + Ui.i(t.get("level")) + " of 5)");
        boolean maxed = Ui.s(t.get("nextName")).isEmpty();
        tierMeter.set(Ui.d(t.get("progress")), maxed ? "Top tier reached" : String.format("%.1f kg to go", Ui.d(t.get("kgToNext"))));
        tierNote.setText(maxed ? "Thank you - you are a Platinum Recycler." : "Next: " + t.get("nextName") + " at " + Ui.d(t.get("nextKg")) + " kg handed in.");

        badges.removeAll();
        for (Map<String, Object> b : ApiClient.list(d.get("badges"))) {
            boolean earned = Boolean.TRUE.equals(b.get("earned"));
            JLabel l = new JLabel("<html><b>" + (earned ? "★ " : "☆ ") + Ui.s(b.get("label")) + "</b><br><span style='font-size:90%'>" + Ui.s(b.get("description")) + "</span></html>");
            l.setOpaque(true);
            l.setBackground(earned ? new Color(0xFFF8E1) : new Color(0xF1F3F4));
            l.setForeground(earned ? new Color(0x5D4037) : new Color(0x90A4AE));
            l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(earned ? new Color(0xFFD54F) : Palette.BORDER), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
            l.setToolTipText(earned ? "Earned" : "Not earned yet");
            badges.add(l);
        }
        badges.revalidate();
        badges.repaint();

        Map<String, Object> p = ApiClient.obj(d.get("pipeline"));
        pipeline.setData(List.of("Submitted", "Pickup scheduled", "Collected", "In progress", "Completed", "Rejected"),
                List.of(Ui.d(p.get("submitted")), Ui.d(p.get("scheduled")), Ui.d(p.get("collected")), Ui.d(p.get("inProgress")), Ui.d(p.get("completed")), Ui.d(p.get("rejected"))));
        List<String> ml = new ArrayList<>();
        for (Object o : (List<?>) d.get("monthlyLabels")) ml.add(Ui.s(o));
        monthly.setData(ml, DashboardPage.nums(d.get("monthlyKg")));

        Map<String, Object> e = ApiClient.obj(d.get("equivalents"));
        equiv.setText(String.format("Your impact so far is like planting %.1f trees, charging a phone %,.0f times, or taking a car off the road for %.0f km.",
                Ui.d(e.get("trees")), Ui.d(e.get("phoneCharges")), Ui.d(e.get("carKm"))));

        drives.removeAll();
        List<Map<String, Object>> cs = ApiClient.list(d.get("campaigns"));
        if (cs.isEmpty()) drives.add(new JLabel("No collection drive is running right now."));
        for (Map<String, Object> c : cs) {
            JPanel row = new JPanel(new BorderLayout(10, 2));
            row.setAlignmentX(0f);
            JLabel name = new JLabel("<html><b>" + esc(Ui.s(c.get("name"))) + "</b> - " + Ui.i(c.get("daysLeft")) + " days left, " + Ui.i(c.get("contributors")) + " contributors</html>");
            Meter m = new Meter();
            m.set(Ui.d(c.get("percent")) / 100.0, String.format("%.1f of %.0f kg", Ui.d(c.get("progressKg")), Ui.d(c.get("goalKg"))));
            m.setColor(Palette.series(1));
            row.add(name, BorderLayout.NORTH);
            row.add(m, BorderLayout.CENTER);
            row.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
            drives.add(row);
        }
        drives.revalidate();
        drives.repaint();
        recent.setRows(ApiClient.list(d.get("recent")));
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private void saveCertificate() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("impact-certificate.html"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        Ui.run(this, () -> {
            java.nio.file.Files.write(f.toPath(), app.api.getText("/api/me/certificate").getBytes(StandardCharsets.UTF_8));
            return f;
        }, saved -> Ui.info(this, "Saved " + saved.getName() + ". Open it in a browser to view or print it."));
    }
}
