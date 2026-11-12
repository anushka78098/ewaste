package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class MarketAdminPage extends Page {
    private final MapTable table = new MapTable(num("Listing", m -> m.get("id")), num("Item ID", m -> m.get("itemId")), key("Item", "itemName"),
            key("Category", "categoryLabel"), key("Condition", "conditionLabel"), num("Price", m -> Ui.money(m.get("price"))));

    public MarketAdminPage(App app) {
        super(app, "Market listings");
        add(top("Active market listings", Ui.button("Refresh", this::refresh), Ui.primary("Change price...", this::reprice),
                Ui.button("Remove listing", this::remove)), BorderLayout.NORTH);
        add(table.scroll(), BorderLayout.CENTER);
        add(new JLabel("<html><i>Lowering a price notifies every user about the price drop.</i></html>"), BorderLayout.SOUTH);
    }

    @Override public void refresh() { Ui.run(this, () -> ApiClient.list(app.api.get("/api/market")), table::setRows); }

    private void reprice() {
        Map<String, Object> m = table.selected();
        if (m == null) { Ui.info(this, "Select a listing first."); return; }
        String p = JOptionPane.showInputDialog(this, "New price for " + m.get("itemName") + ":", String.format("%.2f", Ui.d(m.get("price"))));
        if (p == null) return;
        try {
            double v = Double.parseDouble(p.trim());
            Ui.run(this, () -> app.api.put("/api/admin/listings/" + m.get("id"), ApiClient.map("price", v)), r -> refresh());
        } catch (NumberFormatException e) { Ui.info(this, "Enter a number."); }
    }

    private void remove() {
        Map<String, Object> m = table.selected();
        if (m == null) { Ui.info(this, "Select a listing first."); return; }
        if (Ui.confirm(this, "Remove \"" + m.get("itemName") + "\" from the market?"))
            Ui.run(this, () -> app.api.delete("/api/admin/listings/" + m.get("id")), r -> refresh());
    }
}
