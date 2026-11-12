package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Browse open recycler auctions, review the recycler's own bid and submit one bid per auction. */
public class RecyclerAuctionsPage extends Page {
    private final MapTable table = new MapTable(num("Auction", m -> m.get("id")), key("Item", "itemName"),
            num("Minimum bid", m -> Ui.money(m.get("minimumBid"))), key("Closes at", "closesAt"),
            key("Status", "status"), num("Bids", m -> m.get("bidCount")),
            col("Winning bid", m -> {
                Map<String, Object> winner = ApiClient.obj(m.get("winningBid"));
                return winner == null ? "-" : Ui.money(winner.get("amount"));
            }), col("My bid", m -> {
                Map<String, Object> bid = ApiClient.obj(m.get("myBid"));
                return bid == null ? "-" : Ui.money(bid.get("amount")) + " (" + bid.get("status") + ")";
            }));

    public RecyclerAuctionsPage(App app) {
        super(app, "Recycler auctions");
        add(top("Available auctions and your results", Ui.button("Refresh", this::refresh),
                Ui.primary("Place bid...", this::placeBid)), BorderLayout.NORTH);
        add(table.scroll(), BorderLayout.CENTER);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/auctions")), table::setRows);
    }

    private void placeBid() {
        Map<String, Object> auction = table.selected();
        if (auction == null) { Ui.info(this, "Select an open auction first."); return; }
        if (!"OPEN".equals(auction.get("status"))) { Ui.info(this, "This auction is no longer open."); return; }
        if (auction.get("myBid") != null) { Ui.info(this, "You have already placed a bid on this auction."); return; }
        JTextField amount = new JTextField(String.valueOf(auction.get("minimumBid")), 18);
        JTextField date = new JTextField(java.time.LocalDate.now().plusDays(1).toString(), 18);
        JTextField method = new JTextField(18);
        Ui.Form form = new Ui.Form();
        form.add("Bid amount *", amount).add("Proposed collection date * (yyyy-MM-dd)", date)
                .add("Recycling method *", method);
        if (JOptionPane.showConfirmDialog(this, form, "Place a bid on auction #" + auction.get("id"),
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            double value = Double.parseDouble(amount.getText().trim());
            Ui.run(this, () -> app.api.post("/api/auctions/" + auction.get("id") + "/bids",
                    ApiClient.map("amount", value, "proposedCollectionDate", date.getText().trim(),
                            "recyclingMethod", method.getText().trim())), result -> refresh());
        } catch (NumberFormatException e) {
            Ui.info(this, "Enter a valid bid amount.");
        }
    }
}
