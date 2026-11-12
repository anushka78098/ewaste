package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Pickups, collected stock, refurbishment and publishing to the market. */
public class InventoryPage extends Page {
    private final MapTable table = new MapTable(num("ID", m -> m.get("id")), key("Owner", "ownerName"), key("Item", "name"),
            num("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))), key("Status", "statusLabel"), key("Plan", "decision"),
            col("Pickup", m -> Ui.s(m.get("pickupDate"))), num("Est. value", m -> Ui.money(m.get("estimatedValue"))));
    private final JButton collect = Ui.primary("Mark collected", this::collect), plan = Ui.button("Change plan...", this::changePlan),
            startRef = Ui.button("Start refurbish...", this::startRefurbish), doneRef = Ui.button("Complete refurbish", this::completeRefurbish),
            publish = Ui.primary("Publish to market...", this::publish);

    public InventoryPage(App app) {
        super(app, "Pickups & stock");
        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Scheduled pickups and collected stock"), BorderLayout.NORTH);
        north.add(Ui.buttonRow(Ui.button("Refresh", this::refresh), collect, plan, startRef, doneRef, publish), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(table.scroll(), BorderLayout.CENTER);
        add(new JLabel("<html><i>Flow: pickup scheduled → mark collected (owner earns points) → reuse items are published; repair items are refurbished first; "
                + "recycle items go to the Recycling tab.</i></html>"), BorderLayout.SOUTH);
        table.onSelect(m -> updateButtons());
        updateButtons();
    }

    private void updateButtons() {
        Map<String, Object> m = table.selected();
        String st = m == null ? "" : Ui.s(m.get("status")), dec = m == null ? "" : Ui.s(m.get("decision"));
        collect.setEnabled(st.equals("PICKUP_SCHEDULED"));
        plan.setEnabled(st.equals("COLLECTED"));
        startRef.setEnabled(st.equals("COLLECTED") && dec.equals("REPAIR_REUSE"));
        doneRef.setEnabled(st.equals("REFURBISHING"));
        publish.setEnabled(st.equals("COLLECTED") && dec.equals("REUSE"));
    }

    @Override public void refresh() {
        Ui.run(this, () -> {
            List<Map<String, Object>> keep = new ArrayList<>();
            for (Map<String, Object> m : ApiClient.list(app.api.get("/api/admin/items"))) {
                String s = Ui.s(m.get("status"));
                if (s.equals("PICKUP_SCHEDULED") || s.equals("COLLECTED") || s.equals("REFURBISHING")) keep.add(m);
            }
            return keep;
        }, rows -> { table.setRows(rows); updateButtons(); });
    }

    private Map<String, Object> need() {
        Map<String, Object> m = table.selected();
        if (m == null) Ui.info(this, "Select an item first.");
        return m;
    }

    private void post(String path, Object body, String okMsg) {
        Ui.run(this, () -> app.api.post(path, body), r -> { if (okMsg != null) Ui.info(this, okMsg); refresh(); }, this::refresh);
    }

    private void collect() {
        Map<String, Object> m = need();
        if (m != null) Ui.run(this, () -> ApiClient.obj(app.api.post("/api/admin/items/" + m.get("id") + "/collect", null)),
                (Map<String, Object> r) -> { Ui.info(this, r.get("pointsAwarded") + " reward points awarded to the owner."); refresh(); }, this::refresh);
    }

    private void changePlan() {
        Map<String, Object> m = need();
        if (m == null) return;
        JComboBox<Ui.Opt> plan = Ui.combo(app.decisions(), null);
        if (JOptionPane.showConfirmDialog(this, plan, "New plan for #" + m.get("id"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
            post("/api/admin/items/" + m.get("id") + "/decision", ApiClient.map("decision", Ui.sel(plan)), null);
    }

    private void startRefurbish() {
        Map<String, Object> m = need();
        if (m == null) return;
        String cost = JOptionPane.showInputDialog(this, "Estimated repair cost:", "0");
        if (cost == null) return;
        try { post("/api/admin/items/" + m.get("id") + "/refurbish/start", ApiClient.map("repairCost", Double.parseDouble(cost.trim())), null); }
        catch (NumberFormatException e) { Ui.info(this, "Enter a number."); }
    }

    private void completeRefurbish() {
        Map<String, Object> m = need();
        if (m != null) post("/api/admin/items/" + m.get("id") + "/refurbish/complete", null, "Refurbished. The item can now be published.");
    }

    private void publish() {
        Map<String, Object> m = need();
        if (m == null) return;
        String price = JOptionPane.showInputDialog(this, "Market price (suggested " + Ui.money(m.get("estimatedValue")) + "):",
                String.format("%.2f", Ui.d(m.get("estimatedValue"))));
        if (price == null) return;
        try {
            post("/api/admin/listings", ApiClient.map("itemId", m.get("id"), "price", Double.parseDouble(price.trim())),
                    "Published. All users have been notified.");
        } catch (NumberFormatException e) { Ui.info(this, "Enter a number."); }
    }
}
