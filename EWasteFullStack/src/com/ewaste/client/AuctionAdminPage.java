package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Admin publication, bid comparison, awarding and audit view for recycler auctions. */
public class AuctionAdminPage extends Page {
    private final MapTable eligible = new MapTable(num("Item ID", m -> m.get("id")), key("Item", "name"),
            key("Owner", "ownerName"), key("Category", "categoryLabel"), num("Kg", m -> m.get("weightKg")));
    private final MapTable auctions = new MapTable(num("Auction", m -> m.get("id")), key("Item", "itemName"),
            num("Minimum bid", m -> Ui.money(m.get("minimumBid"))), key("Closes", "closesAt"), key("Status", "status"),
            num("Bids", m -> m.get("bidCount")), col("Winning bid", m -> {
                Map<String, Object> winner = ApiClient.obj(m.get("winningBid"));
                return winner == null ? "-" : Ui.money(winner.get("amount"));
            }));

    public AuctionAdminPage(App app) {
        super(app, "Recycler auctions");
        JPanel eligiblePanel = new JPanel(new BorderLayout(6, 6));
        eligiblePanel.setBorder(BorderFactory.createTitledBorder("Eligible collected recycling items"));
        eligiblePanel.add(eligible.scroll(), BorderLayout.CENTER);
        JPanel auctionPanel = new JPanel(new BorderLayout(6, 6));
        auctionPanel.setBorder(BorderFactory.createTitledBorder("Auctions and results"));
        auctionPanel.add(auctions.scroll(), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eligiblePanel, auctionPanel);
        split.setResizeWeight(0.42);
        add(top("Recycler reverse auctions", Ui.button("Refresh", this::refresh),
                Ui.primary("Publish selected item...", this::publish), Ui.button("Compare / award bids...", this::compare),
                Ui.button("Cancel auction", this::cancel), Ui.button("Audit history", this::history)), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.map("eligible", ApiClient.list(app.api.get("/api/admin/auctions/eligible")),
                "auctions", ApiClient.list(app.api.get("/api/admin/auctions"))), result -> {
            eligible.setRows(ApiClient.list(result.get("eligible")));
            auctions.setRows(ApiClient.list(result.get("auctions")));
        });
    }

    private void publish() {
        Map<String, Object> item = eligible.selected();
        if (item == null) { Ui.info(this, "Select an eligible item first."); return; }
        JTextField minimum = new JTextField("1.00", 18);
        JTextField closes = new JTextField(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString(), 18);
        Ui.Form form = new Ui.Form();
        form.add("Item", new JLabel("#" + item.get("id") + " - " + item.get("name")))
                .add("Minimum bid *", minimum).add("Close at * (yyyy-MM-ddTHH:mm)", closes);
        if (JOptionPane.showConfirmDialog(this, form, "Publish recycler auction", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            double amount = Double.parseDouble(minimum.getText().trim());
            Ui.run(this, () -> app.api.post("/api/admin/auctions", ApiClient.map("itemId", item.get("id"),
                    "minimumBid", amount, "closesAt", closes.getText().trim())), result -> refresh());
        } catch (NumberFormatException e) {
            Ui.info(this, "Enter a valid minimum bid amount.");
        }
    }

    private void compare() {
        Map<String, Object> auction = auctions.selected();
        if (auction == null) { Ui.info(this, "Select an auction first."); return; }
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/admin/auctions/" + auction.get("id") + "/bids")), bids -> {
            if (bids.isEmpty()) { Ui.info(this, "No bids have been placed on this auction."); return; }
            MapTable table = new MapTable(num("Bid ID", m -> m.get("id")), key("Recycler", "recyclerName"),
                    num("Amount", m -> Ui.money(m.get("amount"))), key("Collection date", "proposedCollectionDate"),
                    key("Method", "recyclingMethod"), key("Submitted", "bidTimestamp"), key("Status", "status"));
            table.setRows(bids);
            int choice = JOptionPane.showOptionDialog(this, table.scroll(), "Compare bids for auction #" + auction.get("id"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new Object[]{"Award selected bid", "Close"}, "Close");
            if (choice != 0) return;
            Map<String, Object> bid = table.selected();
            if (bid == null) { Ui.info(this, "Select a bid to award."); return; }
            if (Ui.confirm(this, "Award bid #" + bid.get("id") + " to " + bid.get("recyclerName")
                    + "? All competing bids will be rejected and both parties will be notified.")) {
                Ui.run(this, () -> app.api.post("/api/admin/auctions/" + auction.get("id") + "/award",
                        ApiClient.map("bidId", bid.get("id"))), result -> refresh());
            }
        });
    }

    private void cancel() {
        Map<String, Object> auction = auctions.selected();
        if (auction == null) { Ui.info(this, "Select an auction first."); return; }
        if (Ui.confirm(this, "Cancel auction #" + auction.get("id") + "? Pending bids will be rejected."))
            Ui.run(this, () -> app.api.post("/api/admin/auctions/" + auction.get("id") + "/cancel", null), result -> refresh());
    }

    private void history() {
        Map<String, Object> auction = auctions.selected();
        if (auction == null) { Ui.info(this, "Select an auction first."); return; }
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/admin/auctions/" + auction.get("id") + "/history")), entries -> {
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> entry : entries)
                text.append(entry.get("timestamp")).append("  ").append(entry.get("action")).append("  ")
                        .append(entry.get("actorName")).append(": ").append(entry.get("details")).append('\n');
            JTextArea area = Ui.readOnlyArea(Math.max(4, Math.min(20, entries.size() + 1)));
            area.setText(text.toString());
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Auction audit history", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
