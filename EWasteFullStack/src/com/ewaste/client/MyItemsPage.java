package com.ewaste.client;

import javax.swing.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class MyItemsPage extends Page {
    private final MapTable table = new MapTable(num("ID", m -> m.get("id")), key("Item", "name"), key("Tracking", "trackingCode"), key("Category", "categoryLabel"),
            num("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))), key("Status", "statusLabel"),
            col("Plan", m -> m.get("decision") != null ? m.get("decision") : "(suggested: " + m.get("suggestedDecision") + ")"),
            col("Pickup", m -> Ui.s(m.get("pickupDate"))));
    private final JTextArea detail = Ui.readOnlyArea(10);

    public MyItemsPage(App app) {
        super(app, "My items");
        add(top("My items and their progress", Ui.button("Refresh", this::refresh)), java.awt.BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, table.scroll(), new JScrollPane(detail));
        split.setResizeWeight(0.55);
        add(split, java.awt.BorderLayout.CENTER);
        detail.setText("Select an item to see its full timeline.");
        table.onSelect(m -> {
            if (m == null) return;
            Ui.run(this, () -> ApiClient.obj(app.api.get("/api/items/" + m.get("id"))), (Map<String, Object> full) -> {
                detail.setText(Ui.itemDetail(full));
                detail.setCaretPosition(0);
            });
        });
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/items/mine")), table::setRows);
    }
}
