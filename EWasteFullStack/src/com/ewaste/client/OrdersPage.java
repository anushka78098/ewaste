package com.ewaste.client;

import static com.ewaste.client.MapTable.*;

public class OrdersPage extends Page {
    private final MapTable table = new MapTable(num("Order", m -> m.get("id")), key("Item", "itemName"),
            num("List price", m -> Ui.money(m.get("listPrice"))), num("Points used", m -> m.get("pointsUsed")),
            num("Paid", m -> Ui.money(m.get("amountPaid"))), col("Date", m -> Ui.date(m.get("createdAt"))));

    public OrdersPage(App app) {
        super(app, "My orders");
        add(top("Orders", Ui.button("Refresh", this::refresh)), java.awt.BorderLayout.NORTH);
        add(table.scroll(), java.awt.BorderLayout.CENTER);
    }

    @Override public void refresh() { Ui.run(this, () -> ApiClient.list(app.api.get("/api/orders/mine")), table::setRows); }
}
