package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
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
            first = new KpiCard("active listings", 0);
            second = new KpiCard("items sold", 1);
            third = new KpiCard("sales to date", 2);
            table = new MapTable(num("Listing", m -> m.get("listingId")), key("Item", "itemName"),
                    num("Price", m -> Ui.money(m.get("price"))), col("Status", m -> Boolean.TRUE.equals(m.get("active")) ? "Listed" : Boolean.TRUE.equals(m.get("sold")) ? "Sold" : "Closed"));
            add(top("Your marketplace listings", Ui.primary("Create listing...", this::createListing),
                    Ui.button("Upload photo...", this::uploadPhoto), Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        } else {
            first = new KpiCard("orders placed", 0);
            second = new KpiCard("total spent", 1);
            third = new KpiCard("market savings", 2);
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

    private void createListing() {
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/me/listings/eligible")), eligible -> {
            if (eligible.isEmpty()) {
                Ui.info(this, "There are no eligible items to list yet. An item must be collected and approved for reuse first.");
                return;
            }
            JComboBox<String> itemPicker = new JComboBox<>();
            for (Map<String, Object> item : eligible)
                itemPicker.addItem("#" + item.get("id") + "  " + item.get("name") + " (" + item.get("categoryLabel") + ")");
            JTextField price = new JTextField(12);
            JLabel photoName = new JLabel("No photo selected (optional)");
            Path[] photoPath = new Path[1];
            JButton choosePhoto = Ui.button("Choose PNG/JPEG...", () -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose a listing photo (maximum 4 MB)");
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG or JPEG images", "png", "jpg", "jpeg"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    photoPath[0] = chooser.getSelectedFile().toPath();
                    photoName.setText(photoPath[0].getFileName().toString());
                }
            });
            JPanel photoRow = new JPanel(new BorderLayout(6, 0));
            photoRow.add(choosePhoto, BorderLayout.WEST);
            photoRow.add(photoName, BorderLayout.CENTER);
            Ui.Form form = new Ui.Form().add("Collected item", itemPicker).add("Price ($)", price).add("Photo", photoRow);
            if (JOptionPane.showConfirmDialog(this, form, "Create marketplace listing",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            Map<String, Object> selected = eligible.get(itemPicker.getSelectedIndex());
            double amount;
            try { amount = Double.parseDouble(price.getText().trim()); }
            catch (NumberFormatException e) { Ui.info(this, "Enter a valid listing price."); return; }
            if (!Double.isFinite(amount) || amount <= 0 || amount > 10_000_000) {
                Ui.info(this, "Price must be greater than zero and no more than $10,000,000.");
                return;
            }
            Path selectedPhoto = photoPath[0];
            Ui.run(this, () -> {
                Map<String, Object> body = ApiClient.map("itemId", selected.get("id"), "price", amount);
                if (selectedPhoto != null) body.putAll(ApiClient.filePayload(selectedPhoto));
                return ApiClient.obj(app.api.post("/api/me/listings", body));
            }, result -> { Ui.info(this, "Listing published."); refresh(); });
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
