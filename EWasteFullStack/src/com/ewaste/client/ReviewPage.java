package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class ReviewPage extends Page {
    private final MapTable table = new MapTable(num("ID", m -> m.get("id")), key("Owner", "ownerName"), key("Item", "name"),
            key("Category", "categoryLabel"), num("Age", m -> m.get("ageYears")), num("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))),
            key("Condition", "conditionLabel"), key("Suggested plan", "suggestedDecision"));
    private final JTextArea detail = Ui.readOnlyArea(9);

    public ReviewPage(App app) {
        super(app, "Review submissions");
        add(top("Submitted items awaiting review", Ui.button("Refresh", this::refresh),
                Ui.primary("Approve & schedule pickup...", this::approve), Ui.button("Reject...", this::reject)), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, table.scroll(), new JScrollPane(detail));
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
        table.onSelect(m -> detail.setText(m == null ? "" : Ui.itemDetail(m)));
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/admin/items?status=SUBMITTED")), rows -> {
            table.setRows(rows);
            if (rows.isEmpty()) detail.setText("Nothing to review - all submissions have been processed.");
        });
    }

    private void approve() {
        Map<String, Object> it = table.selected();
        if (it == null) { Ui.info(this, "Select an item first."); return; }
        JComboBox<Ui.Opt> plan = Ui.combo(app.decisions(), null);
        for (int i = 0; i < plan.getItemCount(); i++) if (plan.getItemAt(i).name.equals(Ui.s(it.get("suggestedDecision")))) plan.setSelectedIndex(i);
        JTextField date = new JTextField(LocalDate.now().plusDays(1).toString(), 12);
        JTextField note = new JTextField(24);
        Ui.Form f = new Ui.Form();
        f.add("Item", new JLabel("#" + it.get("id") + " " + it.get("name"))).add("Plan", plan).add("Pickup date (yyyy-mm-dd)", date).add("Note to user", note);
        if (JOptionPane.showConfirmDialog(this, f, "Approve item", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        Map<String, Object> body = ApiClient.map("decision", Ui.sel(plan), "pickupDate", date.getText().trim(), "note", note.getText().trim());
        Ui.run(this, () -> app.api.post("/api/admin/items/" + it.get("id") + "/approve", body), r -> refresh());
    }

    private void reject() {
        Map<String, Object> it = table.selected();
        if (it == null) { Ui.info(this, "Select an item first."); return; }
        String reason = JOptionPane.showInputDialog(this, "Reason for rejecting #" + it.get("id") + " (the user will see this):");
        if (reason == null) return;
        Ui.run(this, () -> app.api.post("/api/admin/items/" + it.get("id") + "/reject", ApiClient.map("reason", reason)), r -> refresh());
    }
}
