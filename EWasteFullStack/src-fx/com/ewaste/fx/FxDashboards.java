package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The two dashboards, drawn with the built-in JavaFX charts. Same data as the Swing ones (/api/admin/analytics, /api/me/dashboard). */
final class FxDashboards {
    private FxDashboards() {}

    private static List<Double> nums(Object list) {
        List<Double> out = new ArrayList<>();
        for (Object o : (List<?>) list) out.add(Ui.d(o));
        return out;
    }

    private static List<String> col(List<Map<String, Object>> rows, String key) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(Ui.s(r.get(key)));
        return out;
    }

    private static List<Double> colNum(List<Map<String, Object>> rows, String key) {
        List<Double> out = new ArrayList<>();
        for (Map<String, Object> r : rows) out.add(Ui.d(r.get(key)));
        return out;
    }

    private static ScrollPane scroll(Node n) {
        ScrollPane sp = new ScrollPane(n);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent;");
        return sp;
    }

    private static GridPane grid(int cols, Node... nodes) {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(cc);
        }
        for (int i = 0; i < nodes.length; i++) {
            g.add(nodes[i], i % cols, i / cols);
            GridPane.setFillWidth(nodes[i], true);
            if (nodes[i] instanceof Region) ((Region) nodes[i]).setMaxWidth(Double.MAX_VALUE);
        }
        return g;
    }

    // ================================================================== admin
    static final class Admin extends FxPage {
        private static final int[] DAYS = {7, 30, 90, 180};
        private final ComboBox<String> period = new ComboBox<>();
        private final VBox alerts = new VBox(4);
        private final Fx.Kpi kSubmitted = new Fx.Kpi("items submitted"), kKg = new Fx.Kpi("e-waste submitted"), kRevenue = new Fx.Kpi("market revenue"),
                kRecycled = new Fx.Kpi("recycled"), kCo2 = new Fx.Kpi("CO2 avoided");
        private final Label totals = Fx.muted(" ");
        private final BarChart<String, Number> submitted = Charts.bar("Items submitted", "#2563EB");
        private final LineChart<String, Number> revenue = Charts.line("Market revenue ($)", "#8B5CF6");
        private final PieChart outcomes = Charts.pie("What happens to items");
        private final BarChart<Number, String> categories = Charts.hbar("E-waste by category (kg)", "#D29B00");
        private final BarChart<Number, String> funnel = Charts.hbar("Item funnel", "#0F9D8A");
        private final BarChart<Number, String> donors = Charts.hbar("Top donors (kg collected)", "#0783C4");
        private final Fx.Grid status = new Fx.Grid().key("Status", "label").col("Items", m -> m.get("count"));
        private final Fx.Grid materials = new Fx.Grid().key("Material", "label").col("Recovered (kg)", m -> String.format("%.1f", Ui.d(m.get("kg"))));
        private boolean loading;

        Admin(App app) {
            super(app, "Dashboard");
            kSubmitted.setAccent("#4267D5", "#6688F0", "#FFFFFF");
            kKg.setAccent("#8150D8", "#AA70E8", "#FFFFFF");
            kRevenue.setAccent("#A86F00", "#CC941B", "#FFFFFF");
            kRecycled.setAccent("#0B807F", "#20A5A1", "#FFFFFF");
            kCo2.setAccent("#1474A6", "#2D9BC8", "#FFFFFF");
            period.getItems().addAll("Last 7 days", "Last 30 days", "Last 90 days", "Last 180 days");
            period.getSelectionModel().select(1);
            period.setOnAction(e -> refresh());
            VBox alertBox = new VBox(4, new Label("Needs attention"), alerts);
            alertBox.setPadding(new Insets(8));
            alertBox.setStyle("-fx-border-color:#CFD8DC;");
            status.setPrefHeight(170);
            materials.setPrefHeight(170);
            VBox body = new VBox(10, alertBox, grid(5, kSubmitted, kKg, kRevenue, kRecycled, kCo2), totals,
                    grid(2, submitted, revenue, outcomes, categories, funnel, donors),
                    grid(2, new VBox(4, Fx.heading("Items by status"), status), new VBox(4, Fx.heading("Materials recovered by recycling"), materials)));
            body.setPadding(new Insets(0, 8, 0, 0));
            setTop(header("Overview", new Label("Period:"), period, Fx.button("Refresh", this::refresh)));
            setCenter(scroll(body));
        }

        @Override void refresh() {
            if (loading) return;
            loading = true;
            int days = DAYS[Math.max(0, period.getSelectionModel().getSelectedIndex())];
            Fx.run(() -> new Object[]{ApiClient.obj(app.api.get("/api/admin/analytics?days=" + days)), ApiClient.obj(app.api.get("/api/admin/stats"))},
                    r -> { loading = false; show(ApiClient.obj(r[0]), ApiClient.obj(r[1]), days); }, () -> loading = false);
        }

        private void show(Map<String, Object> a, Map<String, Object> s, int days) {
            alerts.getChildren().clear();
            for (Map<String, Object> al : ApiClient.list(a.get("alerts"))) {
                String level = Ui.s(al.get("level")), tab = Ui.s(al.get("tab"));
                String color = "warn".equals(level) ? "#E65100" : "ok".equals(level) ? "#2E7D32" : "#1565C0";
                String mark = "warn".equals(level) ? "⚠ " : "ok".equals(level) ? "✔ " : "• ";
                Label l = new Label(mark + Ui.s(al.get("text")));
                l.setStyle("-fx-text-fill:" + color + ";");
                HBox row = new HBox(8, l);
                if (!tab.isEmpty()) row.getChildren().add(Fx.button("Open " + tab, () -> app.goTo.accept(tab)));
                alerts.getChildren().add(row);
            }
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

            List<String> labels = new ArrayList<>();
            for (Object o : (List<?>) a.get("labels")) { String d = Ui.s(o); labels.add(d.length() >= 10 ? d.substring(5, 10) : d); }
            Charts.setBar(submitted, labels, nums(a.get("submitted")));
            Charts.setLine(revenue, labels, nums(a.get("revenue")));
            List<Map<String, Object>> out = ApiClient.list(a.get("outcomes"));
            Charts.setPie(outcomes, col(out, "label"), colNum(out, "count"));
            List<Map<String, Object>> cats = ApiClient.list(a.get("byCategory"));
            Charts.setHbar(categories, col(cats, "label"), colNum(cats, "kg"));
            List<Map<String, Object>> fun = ApiClient.list(a.get("funnel"));
            Charts.setHbar(funnel, col(fun, "label"), colNum(fun, "count"));
            List<Map<String, Object>> top = ApiClient.list(a.get("topDonors"));
            Charts.setHbar(donors, col(top, "label"), colNum(top, "kg"));

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

    // ================================================================== user
    static final class User extends FxPage {
        private final Fx.Kpi kKg = new Fx.Kpi("handed in"), kPoints = new Fx.Kpi("reward points"), kCo2 = new Fx.Kpi("CO2 avoided"), kItems = new Fx.Kpi("items submitted");
        private final Label tierName = new Label(" "), tierNote = Fx.muted(" ");
        private final ProgressBar tierBar = new ProgressBar(0);
        private final Label tierText = new Label(" ");
        private final FlowPane badges = new FlowPane(8, 8);
        private final PieChart pipeline = Charts.pie("Where my items are");
        private final BarChart<String, Number> monthly = Charts.bar("Handed in per month (kg)", "#2563EB");
        private final Label equiv = new Label(" ");
        private final VBox drives = new VBox(8);
        private final Fx.Grid recent = new Fx.Grid().key("Tracking code", "trackingCode").key("Item", "name").key("Status", "statusLabel");

        User(App app) {
            super(app, "My dashboard");
            kKg.setAccent("#4267D5", "#6688F0", "#FFFFFF");
            kPoints.setAccent("#8150D8", "#AA70E8", "#FFFFFF");
            kCo2.setAccent("#A86F00", "#CC941B", "#FFFFFF");
            kItems.setAccent("#0B807F", "#20A5A1", "#FFFFFF");
            tierName.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + Fx.GREEN_DARK + ";");
            tierBar.setMaxWidth(Double.MAX_VALUE);
            equiv.setWrapText(true);
            recent.setPrefHeight(170);
            VBox tier = new VBox(6, new Label("Reward tier"), tierName, tierBar, tierText, tierNote);
            VBox badgeBox = new VBox(6, new Label("Badges"), badges);
            VBox driveBox = new VBox(6, new Label("Collection drives"), drives);
            VBox recentBox = new VBox(6, new Label("Recent items (share the tracking code to let anyone follow the item)"), recent);
            for (VBox b : new VBox[]{tier, badgeBox, driveBox}) { b.setPadding(new Insets(8)); b.setStyle("-fx-border-color:#CFD8DC;"); }
            VBox body = new VBox(10, grid(4, kKg, kPoints, kCo2, kItems), tier, badgeBox, grid(2, pipeline, monthly), equiv, driveBox, recentBox);
            body.setPadding(new Insets(0, 8, 0, 0));
            setTop(header("My dashboard", Fx.button("Save impact certificate...", this::saveCertificate), Fx.button("Refresh", this::refresh)));
            setCenter(scroll(body));
        }

        @Override void refresh() { Fx.run(() -> ApiClient.obj(app.api.get("/api/me/dashboard")), this::show); }

        private void show(Map<String, Object> d) {
            kKg.set(Ui.kg(d.get("kgDonated")));
            kPoints.set(Ui.i(d.get("points")) + "  (" + Ui.money(d.get("pointsValue")) + ")");
            kCo2.set(Ui.kg(d.get("co2Saved")));
            kItems.set(String.valueOf(Ui.i(d.get("itemCount"))));

            Map<String, Object> t = ApiClient.obj(d.get("tier"));
            tierName.setText(Ui.s(t.get("name")) + "  (level " + Ui.i(t.get("level")) + " of 5)");
            boolean maxed = Ui.s(t.get("nextName")).isEmpty();
            tierBar.setProgress(Ui.d(t.get("progress")));
            tierText.setText(maxed ? "Top tier reached" : String.format("%.1f kg to go", Ui.d(t.get("kgToNext"))));
            tierNote.setText(maxed ? "Thank you - you are a Platinum Recycler." : "Next: " + t.get("nextName") + " at " + Ui.d(t.get("nextKg")) + " kg handed in.");

            badges.getChildren().clear();
            for (Map<String, Object> b : ApiClient.list(d.get("badges"))) {
                boolean earned = Boolean.TRUE.equals(b.get("earned"));
                Label l = new Label((earned ? "★ " : "☆ ") + Ui.s(b.get("label")) + "\n" + Ui.s(b.get("description")));
                l.setWrapText(true);
                l.setPrefWidth(230);
                l.setPadding(new Insets(6, 8, 6, 8));
                l.setStyle(earned ? "-fx-background-color:#FFF8E1;-fx-border-color:#FFD54F;-fx-text-fill:#5D4037;"
                        : "-fx-background-color:#F1F3F4;-fx-border-color:#CFD8DC;-fx-text-fill:#90A4AE;");
                Tooltip.install(l, new Tooltip(earned ? "Earned" : "Not earned yet"));
                badges.getChildren().add(l);
            }

            Map<String, Object> p = ApiClient.obj(d.get("pipeline"));
            Charts.setPie(pipeline, List.of("Submitted", "Pickup scheduled", "Collected", "In progress", "Completed", "Rejected"),
                    List.of(Ui.d(p.get("submitted")), Ui.d(p.get("scheduled")), Ui.d(p.get("collected")), Ui.d(p.get("inProgress")), Ui.d(p.get("completed")), Ui.d(p.get("rejected"))));
            List<String> ml = new ArrayList<>();
            for (Object o : (List<?>) d.get("monthlyLabels")) ml.add(Ui.s(o));
            Charts.setBar(monthly, ml, nums(d.get("monthlyKg")));

            Map<String, Object> e = ApiClient.obj(d.get("equivalents"));
            equiv.setText(String.format("Your impact so far is like planting %.1f trees, charging a phone %,.0f times, or taking a car off the road for %.0f km.",
                    Ui.d(e.get("trees")), Ui.d(e.get("phoneCharges")), Ui.d(e.get("carKm"))));

            drives.getChildren().clear();
            List<Map<String, Object>> cs = ApiClient.list(d.get("campaigns"));
            if (cs.isEmpty()) drives.getChildren().add(new Label("No collection drive is running right now."));
            for (Map<String, Object> c : cs) {
                Label name = new Label(Ui.s(c.get("name")) + " - " + Ui.i(c.get("daysLeft")) + " days left, " + Ui.i(c.get("contributors")) + " contributors");
                name.setStyle("-fx-font-weight:bold;");
                ProgressBar bar = new ProgressBar(Math.min(1, Ui.d(c.get("percent")) / 100.0));
                bar.setMaxWidth(Double.MAX_VALUE);
                Label amount = Fx.muted(String.format("%.1f of %.0f kg", Ui.d(c.get("progressKg")), Ui.d(c.get("goalKg"))));
                drives.getChildren().add(new VBox(2, name, bar, amount));
            }
            recent.setRows(ApiClient.list(d.get("recent")));
        }

        private void saveCertificate() {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setInitialFileName("impact-certificate.html");
            java.io.File f = fc.showSaveDialog(getScene().getWindow());
            if (f == null) return;
            Fx.run(() -> {
                java.nio.file.Files.write(f.toPath(), app.api.getText("/api/me/certificate").getBytes(StandardCharsets.UTF_8));
                return f;
            }, saved -> Fx.info("Saved " + saved.getName() + ". Open it in a browser to view or print it."));
        }
    }
}
