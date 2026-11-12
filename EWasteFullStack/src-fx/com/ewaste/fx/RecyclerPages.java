package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Recycler screens, in addition to the member features already available to every signed-in user. */
final class RecyclerPages {
    private RecyclerPages() {}

    static List<FxPage> create(App app) {
        List<FxPage> pages = new ArrayList<>(UserPages.create(app));
        pages.add(new Auctions(app));
        return pages;
    }

    private static final class Auctions extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("Auction", m -> m.get("id")).key("Item", "itemName")
                .col("Minimum bid", m -> String.format("$%.2f", Ui.d(m.get("minimumBid")))).key("Closes at", "closesAt")
                .key("Status", "status").col("Bids", m -> m.get("bidCount")).col("Winning bid", m -> {
                    Map<String, Object> winner = ApiClient.obj(m.get("winningBid"));
                    return winner == null ? "-" : String.format("$%.2f", Ui.d(winner.get("amount")));
                }).col("My bid", m -> {
                    Map<String, Object> bid = ApiClient.obj(m.get("myBid"));
                    return bid == null ? "-" : String.format("$%.2f", Ui.d(bid.get("amount"))) + " (" + bid.get("status") + ")";
                });

        Auctions(App app) {
            super(app, "Recycler auctions");
            setTop(header("Available auctions and your results", Fx.button("Refresh", this::refresh),
                    Fx.primary("Place bid...", this::placeBid)));
            setCenter(table);
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.list(app.api.get("/api/auctions")), table::setRows);
        }

        private void placeBid() {
            Map<String, Object> auction = table.selected();
            if (auction == null) { Fx.info("Select an auction first."); return; }
            if (!"OPEN".equals(auction.get("status"))) { Fx.info("This auction is no longer open."); return; }
            if (auction.get("myBid") != null) { Fx.info("You have already placed a bid on this auction."); return; }
            TextField amount = new TextField(String.valueOf(auction.get("minimumBid")));
            TextField date = new TextField(LocalDate.now().plusDays(1).toString());
            TextField method = new TextField();
            if (!Fx.dialog("Place a bid on auction #" + auction.get("id"),
                    Fx.form("Bid amount *", amount, "Proposed collection date * (yyyy-MM-dd)", date,
                            "Recycling method *", method))) return;
            try {
                double value = Double.parseDouble(amount.getText().trim());
                Fx.run(() -> app.api.post("/api/auctions/" + auction.get("id") + "/bids",
                        ApiClient.map("amount", value, "proposedCollectionDate", date.getText().trim(),
                                "recyclingMethod", method.getText().trim())), result -> refresh());
            } catch (NumberFormatException e) { Fx.info("Enter a valid bid amount."); }
        }
    }
}
