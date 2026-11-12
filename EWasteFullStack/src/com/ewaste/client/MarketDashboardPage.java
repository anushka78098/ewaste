package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Separate buyer and donor/seller views of marketplace activity. */
public class MarketDashboardPage extends Page {
    private final boolean sellerView;
    private final KpiCard first, second, third;
    private final MapTable table;

    public MarketDashboardPage(App app, boolean sellerView) {
        super(app, sellerView ? "Seller dashboard" : "Buyer dashboard");
        this.sellerView = sellerView;
        if (sellerView) {
            first = new KpiCard("active listings");
            second = new KpiCard("items sold");
            third = new KpiCard("sales to date");
            table = new MapTable(num("Listing", m -> m.get("listingId")), key("Item", "itemName"),
                    num("Price", m -> Ui.money(m.get("price"))), col("Status", m -> Boolean.TRUE.equals(m.get("active")) ? "Listed" : Boolean.TRUE.equals(m.get("sold")) ? "Sold" : "Closed"));
            add(top("Your donated items for sale", Ui.button("Upload photo...", this::uploadPhoto), Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        } else {
            first = new KpiCard("orders placed");
            second = new KpiCard("total spent");
            third = new KpiCard("market savings");
            table = new MapTable(num("Order", m -> m.get("orderId")), key("Item", "itemName"), key("Seller", "sellerName"),
                    num("Paid", m -> Ui.money(m.get("amountPaid"))), col("Date", m -> Ui.date(m.get("createdAt"))));
            add(top("Your marketplace purchases", Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        }
        JPanel cards = new JPanel(new GridLayout(1, 3, 10, 0));
        cards.add(first); cards.add(second); cards.add(third);
        add(cards, BorderLayout.CENTER);
        add(table.scroll(), BorderLayout.SOUTH);
        table.setPreferredScrollableViewportSize(new Dimension(700, 300));
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/me/market/dashboard")), data -> {
            Map<String, Object> dashboard = ApiClient.obj(data.get(sellerView ? "seller" : "buyer"));
            if (sellerView) {
                first.set(String.valueOf(Ui.i(dashboard.get("activeListings"))));
                second.set(String.valueOf(Ui.i(dashboard.get("soldItems"))));
                third.set(Ui.money(dashboard.get("salesTotal")));
                table.setRows(ApiClient.list(dashboard.get("items")));
            } else {
                first.set(String.valueOf(Ui.i(dashboard.get("orders"))));
                second.set(Ui.money(dashboard.get("totalSpent")));
                double savings = 0;
                for (Map<String, Object> order : ApiClient.list(dashboard.get("recent"))) savings += Ui.d(order.get("discount"));
                third.set(Ui.money(savings));
                table.setRows(ApiClient.list(dashboard.get("recent")));
            }
        });
    }

    private void uploadPhoto() {
        if (!sellerView) return;
        Map<String, Object> listing = table.selected();
        if (listing == null) { Ui.info(this, "Select one of your listed items first."); return; }
        if (!Boolean.TRUE.equals(listing.get("active"))) { Ui.info(this, "Only active listings can have their photos updated."); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a PNG or JPEG product photo");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            Ui.info(this, "Choose a PNG or JPEG image.");
            return;
        }
        Ui.run(this, () -> {
            Map<String, Object> body = ApiClient.filePayload(path);
            return ApiClient.obj(app.api.post("/api/market/" + listing.get("listingId") + "/photo", body));
        }, result -> { Ui.info(this, "Photo uploaded."); refresh(); });
    }
}
