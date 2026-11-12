package com.ewaste.client;

import com.ewaste.client.charts.Palette;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Admin: create and end collection drives (campaigns with a kg goal); donors see them on their dashboard. */
public class CampaignsPage extends Page {
    private final MapTable table = new MapTable(num("ID", m -> m.get("id")), key("Drive", "name"), key("Status", "status"),
            key("Ends", "endDate"), num("Goal (kg)", m -> String.format("%.0f", Ui.d(m.get("goalKg")))),
            num("Collected (kg)", m -> String.format("%.1f", Ui.d(m.get("progressKg")))), num("Progress", m -> String.format("%.0f%%", Ui.d(m.get("percent")))),
            num("Contributors", m -> m.get("contributors")));

    public CampaignsPage(App app) {
        super(app, "Drives");
        add(top("Collection drives", Ui.primary("New drive...", this::create), Ui.button("End selected", this::end), Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        add(table.scroll(), BorderLayout.CENTER);
        JLabel hint = new JLabel("Creating a drive notifies every user (in-app and by email). Progress counts items submitted while the drive runs.");
        hint.setForeground(Ui.GREY_TEXT);
        add(hint, BorderLayout.SOUTH);
    }

    @Override public void refresh() { Ui.run(this, () -> ApiClient.list(app.api.get("/api/admin/campaigns")), table::setRows); }

    private void create() {
        JTextField name = new JTextField(22), goal = new JTextField("100", 22), end = new JTextField(LocalDate.now().plusDays(30).toString(), 22);
        JTextField desc = new JTextField(22);
        Ui.Form f = new Ui.Form();
        f.add("Name *", name).add("Description", desc).add("Goal (kg) *", goal).add("Ends (yyyy-mm-dd) *", end);
        if (JOptionPane.showConfirmDialog(this, f, "New collection drive", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        double g;
        try { g = Double.parseDouble(goal.getText().trim()); } catch (NumberFormatException e) { Ui.info(this, "Goal must be a number of kilograms."); return; }
        Map<String, Object> body = ApiClient.map("name", name.getText().trim(), "description", desc.getText().trim(), "goalKg", g, "endDate", end.getText().trim());
        Ui.run(this, () -> app.api.post("/api/admin/campaigns", body), r -> refresh());
    }

    private void end() {
        Map<String, Object> m = table.selected();
        if (m == null) { Ui.info(this, "Select a drive first."); return; }
        if (!Ui.confirm(this, "End the drive \"" + m.get("name") + "\"?")) return;
        Ui.run(this, () -> app.api.post("/api/admin/campaigns/" + m.get("id") + "/end", ApiClient.map()), r -> refresh());
    }
}
