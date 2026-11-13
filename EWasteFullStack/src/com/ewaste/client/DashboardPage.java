package com.ewaste.client;

import com.ewaste.client.charts.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Admin dashboard: what needs attention, KPIs against the previous period, trends and breakdowns. */
public class DashboardPage extends Page {
    private final JComboBox<String> period = new JComboBox<>(new String[]{"Last 7 days", "Last 30 days", "Last 90 days", "Last 180 days"});
    private static final int[] DAYS = {7, 30, 90, 180};
    private final JPanel alerts = new JPanel();
    private final KpiCard kSubmitted = new KpiCard("items submitted", 0), kKg = new KpiCard("e-waste submitted", 1),
            kRevenue = new KpiCard("market revenue", 2), kRecycled = new KpiCard("recycled", 3), kCo2 = new KpiCard("CO2 avoided", 4);
    private final JLabel totals = new JLabel(" ");
    private final BarChart submitted = new BarChart("Items submitted");
    private final LineChart revenue = new LineChart("Market revenue ($)");
    private final DonutChart outcomes = new DonutChart("What happens to items");
    private final HBarChart categories = new HBarChart("E-waste by category (kg)");
    private final HBarChart funnel = new HBarChart("Item funnel");
    private final HBarChart donors = new HBarChart("Top donors (kg collected)");
    private final MapTable status = new MapTable(key("Status", "label"), num("Items", m -> m.get("count")));
    private final MapTable materials = new MapTable(key("Material", "label"), num("Recovered (kg)", m -> String.format("%.1f", Ui.d(m.get("kg")))));
    private boolean loading;

    public DashboardPage(App app) {
        super(app, "Dashboard");
        period.setPrototypeDisplayValue("Last 180 days  ");
        period.setSelectedIndex(1);
        period.addActionListener(e -> refresh());
        revenue.setColor(Palette.series(1));
        revenue.setFormat(v -> String.format("$%.2f", v));
        submitted.setFormat(v -> String.valueOf((long) v));
        funnel.setVaryColors(false);
        categories.setColor(Palette.series(0));
        donors.setColor(Palette.series(4));
        funnel.setColor(Palette.series(1));
        donors.setFormat(v -> String.format("%.1f", v));
        categories.setFormat(v -> String.format("%.1f", v));

        alerts.setLayout(new BoxLayout(alerts, BoxLayout.Y_AXIS));
        alerts.setBorder(BorderFactory.createTitledBorder("Needs attention"));

        JPanel kpis = new JPanel(new GridLayout(1, 5, 10, 0));
        for (KpiCard k : new KpiCard[]{kSubmitted, kKg, kRevenue, kRecycled, kCo2}) kpis.add(k);

        JPanel grid = new JPanel(new GridLayout(3, 2, 10, 10));
        grid.add(submitted);
        grid.add(revenue);
        grid.add(outcomes);
        grid.add(categories);
        grid.add(funnel);
        grid.add(donors);

        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 0));
        tables.add(titled("Items by status", status));
        tables.add(titled("Materials recovered by recycling", materials));
        tables.setPreferredSize(new Dimension(100, 170));

        JPanel body = new ScrollPanel(new BorderLayout(0, 10));
        JPanel head = new JPanel(new BorderLayout(0, 8));
        head.add(alerts, BorderLayout.NORTH);
        head.add(kpis, BorderLayout.CENTER);
        head.add(totals, BorderLayout.SOUTH);
        totals.setForeground(Ui.GREY_TEXT);
        body.add(head, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);
        body.add(tables, BorderLayout.SOUTH);
        grid.setPreferredSize(new Dimension(700, 640));

        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        add(top("Overview", new JLabel("Period:"), period, Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        add(sp, BorderLayout.CENTER);
    }

    private static JPanel titled(String title, MapTable t) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(Ui.heading(title), BorderLayout.NORTH);
        p.add(t.scroll(), BorderLayout.CENTER);
        return p;
    }

    static List<Double> nums(Object list) {
        List<Double> out = new ArrayList<>();
        for (Object o : (List<?>) list) out.add(Ui.d(o));
        return out;
    }

    static List<String> col(List<Map<String, Object>> rows, String key) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(Ui.s(r.get(key)));
        return out;
    }

    static List<Double> colNum(List<Map<String, Object>> rows, String key) {
        List<Double> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(Ui.d(r.get(key)));
        return out;
    }

    /** "2026-03-14" becomes "03-14". */
    static String shortDate(String iso) { return iso.length() >= 10 ? iso.substring(5, 10) : iso; }

    @Override public void refresh() {
        if (loading) return;
        loading = true;
        int days = DAYS[Math.max(0, period.getSelectedIndex())];
        Ui.run(this, () -> {
            Map<String, Object> a = ApiClient.obj(app.api.get("/api/admin/analytics?days=" + days));
            Map<String, Object> s = ApiClient.obj(app.api.get("/api/admin/stats"));
            return new Object[]{a, s};
        }, (Object[] r) -> {
            loading = false;
            show(ApiClient.obj(r[0]), ApiClient.obj(r[1]), days);
        }, () -> loading = false);
    }

    private void show(Map<String, Object> a, Map<String, Object> s, int days) {
        // alerts
        alerts.removeAll();
        for (Map<String, Object> al : ApiClient.list(a.get("alerts"))) {
            String level = Ui.s(al.get("level")), tab = Ui.s(al.get("tab"));
            Color c = "warn".equals(level) ? new Color(0xE65100) : "ok".equals(level) ? Palette.GOOD : new Color(0x1565C0);
            String mark = "warn".equals(level) ? "⚠ " : "ok".equals(level) ? "✔ " : "• ";
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
            JLabel l = new JLabel(mark + Ui.s(al.get("text")));
            l.setForeground(c);
            row.add(l);
            if (!tab.isEmpty()) row.add(Ui.button("Open " + tab, () -> app.goTo.accept(tab)));
            alerts.add(row);
        }
        alerts.revalidate();
        alerts.repaint();

        // KPIs
        for (Map<String, Object> k : ApiClient.list(a.get("kpis"))) {
            double cur = Ui.d(k.get("value")), prev = Ui.d(k.get("previous"));
            String unit = Ui.s(k.get("unit")), txt = "money".equals(unit) ? Ui.money(cur) : "kg".equals(unit) ? Ui.kg(cur) : String.valueOf((long) cur);
            switch (Ui.s(k.get("key"))) {
                case "submitted": kSubmitted.set(txt, cur, prev, days); break;
                case "kgIn": kKg.set(txt, cur, prev, days); break;
                case "revenue": kRevenue.set(txt, cur, prev, days); break;
                case "recycledKg": kRecycled.set(txt, cur, prev, days); break;
                default: kCo2.set(txt, cur, prev, days);
            }
        }
        totals.setText(String.format("All time: %d users, %d items, %s handled, diversion rate %.0f%%, %d recycling batches.",
                Ui.i(s.get("users")), Ui.i(s.get("items")), Ui.kg(s.get("totalKg")), Ui.d(s.get("diversionRate")), Ui.i(s.get("batches"))));

        // trends
        List<String> labels = new ArrayList<>();
        for (Object o : (List<?>) a.get("labels")) labels.add(shortDate(Ui.s(o)));
        submitted.setData(labels, nums(a.get("submitted")));
        revenue.setData(labels, nums(a.get("revenue")));

        // breakdowns
        List<Map<String, Object>> out = ApiClient.list(a.get("outcomes"));
        outcomes.setData(col(out, "label"), colNum(out, "count"));
        List<Map<String, Object>> cats = ApiClient.list(a.get("byCategory"));
        categories.setData(col(cats, "label"), colNum(cats, "kg"));
        List<Map<String, Object>> fun = ApiClient.list(a.get("funnel"));
        funnel.setData(col(fun, "label"), colNum(fun, "count"));
        List<Map<String, Object>> top = ApiClient.list(a.get("topDonors"));
        donors.setData(col(top, "label"), colNum(top, "kg"));

        // tables (all-time)
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> by = ApiClient.obj(s.get("byStatus"));
        for (Map<String, Object> st : app.statuses()) rows.add(ApiClient.map("label", st.get("label"), "count", by.get(Ui.s(st.get("name")))));
        status.setRows(rows);
        List<Map<String, Object>> mats = new ArrayList<>();
        for (Map.Entry<String, Object> e : ApiClient.obj(s.get("materials")).entrySet())
            mats.add(ApiClient.map("label", Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1)
                    + ("hazardous".equals(e.getKey()) ? " (safely contained)" : ""), "kg", e.getValue()));
        materials.setRows(mats);
    }
}
