package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Screens for the administrator. */
final class AdminPages {
    private AdminPages() {}

    static List<FxPage> create(App app) {
        return Arrays.asList(new FxDashboards.Admin(app), new FxMarketPages.Dashboard(app, false), new FxMarketPages.Dashboard(app, true),
                new Review(app), new Inventory(app), new Listings(app), new AuctionAdmin(app), new FxMarketPages.Messages(app), new Recycling(app), new Drives(app),
                new AllItems(app), new Users(app), new Announce(app), new UserPages.Profile(app));
    }

    private static Map<String, Object> need(Fx.Grid t) {
        Map<String, Object> m = t.selected();
        if (m == null) Fx.info("Select a row first.");
        return m;
    }

    private static double number(String text) { return Double.parseDouble(text.trim()); }

    // ---- recycler auctions -----------------------------------------------------------------------
    static final class AuctionAdmin extends FxPage {
        private final Fx.Grid eligible = new Fx.Grid().col("Item ID", m -> m.get("id")).key("Item", "name")
                .key("Owner", "ownerName").key("Category", "categoryLabel").col("Kg", m -> m.get("weightKg"));
        private final Fx.Grid auctions = new Fx.Grid().col("Auction", m -> m.get("id")).key("Item", "itemName")
                .col("Minimum bid", m -> String.format("$%.2f", Ui.d(m.get("minimumBid")))).key("Closes at", "closesAt")
                .key("Status", "status").col("Bids", m -> m.get("bidCount")).col("Winning bid", m -> {
                    Map<String, Object> winner = ApiClient.obj(m.get("winningBid"));
                    return winner == null ? "-" : String.format("$%.2f", Ui.d(winner.get("amount")));
                });

        AuctionAdmin(App app) {
            super(app, "Recycler auctions");
            VBox eligibleBox = new VBox(6, Fx.heading("Eligible collected recycling items"), eligible);
            VBox auctionBox = new VBox(6, Fx.heading("Auctions and results"), auctions);
            SplitPane split = new SplitPane(eligibleBox, auctionBox);
            split.setOrientation(javafx.geometry.Orientation.VERTICAL);
            split.setDividerPositions(0.42);
            setTop(header("Recycler reverse auctions", Fx.button("Refresh", this::refresh),
                    Fx.primary("Publish selected item...", this::publish), Fx.button("Compare / award bids...", this::compare),
                    Fx.button("Cancel auction", this::cancel), Fx.button("Audit history", this::history)));
            setCenter(split);
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.map("eligible", ApiClient.list(app.api.get("/api/admin/auctions/eligible")),
                    "auctions", ApiClient.list(app.api.get("/api/admin/auctions"))), result -> {
                eligible.setRows(ApiClient.list(result.get("eligible")));
                auctions.setRows(ApiClient.list(result.get("auctions")));
            });
        }

        private void publish() {
            Map<String, Object> item = need(eligible);
            if (item == null) return;
            TextField minimum = new TextField("1.00");
            TextField closes = new TextField(LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString());
            if (!Fx.dialog("Publish recycler auction", Fx.form("Item", new Label("#" + item.get("id") + " - " + item.get("name")),
                    "Minimum bid *", minimum, "Close at * (yyyy-MM-ddTHH:mm)", closes))) return;
            try {
                double amount = number(minimum.getText());
                Fx.run(() -> app.api.post("/api/admin/auctions", ApiClient.map("itemId", item.get("id"),
                        "minimumBid", amount, "closesAt", closes.getText().trim())), result -> refresh());
            } catch (NumberFormatException e) { Fx.info("Enter a valid minimum bid amount."); }
        }

        private void compare() {
            Map<String, Object> auction = need(auctions);
            if (auction == null) return;
            Fx.run(() -> ApiClient.list(app.api.get("/api/admin/auctions/" + auction.get("id") + "/bids")), bids -> {
                if (bids.isEmpty()) { Fx.info("No bids have been placed on this auction."); return; }
                Fx.Grid table = new Fx.Grid().col("Bid ID", m -> m.get("id")).key("Recycler", "recyclerName")
                        .col("Amount", m -> String.format("$%.2f", Ui.d(m.get("amount")))).key("Collection date", "proposedCollectionDate")
                        .key("Method", "recyclingMethod").key("Submitted", "bidTimestamp").key("Status", "status");
                table.setRows(bids);
                if (!Fx.dialog("Compare bids for auction #" + auction.get("id"), table)) return;
                Map<String, Object> bid = need(table);
                if (bid == null) return;
                if (Fx.confirm("Award bid #" + bid.get("id") + " to " + bid.get("recyclerName")
                        + "? Competing bids will be rejected and both parties notified."))
                    Fx.run(() -> app.api.post("/api/admin/auctions/" + auction.get("id") + "/award",
                            ApiClient.map("bidId", bid.get("id"))), result -> refresh());
            });
        }

        private void cancel() {
            Map<String, Object> auction = need(auctions);
            if (auction != null && Fx.confirm("Cancel auction #" + auction.get("id") + "? Pending bids will be rejected."))
                Fx.run(() -> app.api.post("/api/admin/auctions/" + auction.get("id") + "/cancel", null), result -> refresh());
        }

        private void history() {
            Map<String, Object> auction = need(auctions);
            if (auction == null) return;
            Fx.run(() -> ApiClient.list(app.api.get("/api/admin/auctions/" + auction.get("id") + "/history")), entries -> {
                StringBuilder text = new StringBuilder();
                for (Map<String, Object> entry : entries)
                    text.append(entry.get("timestamp")).append("  ").append(entry.get("action")).append("  ")
                            .append(entry.get("actorName")).append(": ").append(entry.get("details")).append('\n');
                Fx.text("Auction audit history", text.toString());
            });
        }
    }

    // ---- review submissions -----------------------------------------------------------------------
    static final class Review extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("ID", m -> m.get("id")).key("Owner", "ownerName").key("Item", "name").key("Category", "categoryLabel")
                .col("Age", m -> m.get("ageYears")).col("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))).key("Condition", "conditionLabel").key("Suggested plan", "suggestedDecision");
        private final TextArea detail = Fx.readOnlyArea(9);

        Review(App app) {
            super(app, "Review submissions");
            SplitPane split = new SplitPane(table, detail);
            split.setOrientation(javafx.geometry.Orientation.VERTICAL);
            split.setDividerPositions(0.6);
            setTop(header("Submissions waiting for a decision", Fx.primary("Approve...", this::approve), Fx.button("Reject...", this::reject), Fx.button("Refresh", this::refresh)));
            setCenter(split);
            table.onSelect(m -> detail.setText(m == null ? "" : Ui.itemDetail(m)));
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.list(app.api.get("/api/admin/items?status=SUBMITTED")), rows -> {
                table.setRows(rows);
                if (rows.isEmpty()) detail.setText("Nothing to review - all submissions have been processed.");
            });
        }

        private void approve() {
            Map<String, Object> it = need(table);
            if (it == null) return;
            ComboBox<Fx.Opt> plan = Fx.combo(app.decisions(), null);
            Fx.select(plan, Ui.s(it.get("suggestedDecision")));
            TextField date = new TextField(LocalDate.now().plusDays(1).toString()), note = new TextField();
            if (!Fx.dialog("Approve item", Fx.form("Item", new Label("#" + it.get("id") + " " + it.get("name")), "Plan", plan, "Pickup date (yyyy-mm-dd)", date, "Note to user", note))) return;
            Map<String, Object> body = ApiClient.map("decision", Fx.sel(plan), "pickupDate", date.getText().trim(), "note", note.getText().trim());
            Fx.run(() -> app.api.post("/api/admin/items/" + it.get("id") + "/approve", body), r -> refresh());
        }

        private void reject() {
            Map<String, Object> it = need(table);
            if (it == null) return;
            String reason = Fx.ask("Reject item", "Reason for rejecting #" + it.get("id") + " (the user will see this):", "");
            if (reason == null) return;
            Fx.run(() -> app.api.post("/api/admin/items/" + it.get("id") + "/reject", ApiClient.map("reason", reason)), r -> refresh());
        }
    }

    // ---- pickups & stock ----------------------------------------------------------------------------
    static final class Inventory extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("ID", m -> m.get("id")).key("Owner", "ownerName").key("Item", "name")
                .col("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))).key("Status", "statusLabel").key("Plan", "decision")
                .col("Pickup", m -> Ui.s(m.get("pickupDate"))).col("Est. value", m -> Ui.money(m.get("estimatedValue")));

        Inventory(App app) {
            super(app, "Pickups & stock");
            setTop(header("Pickups, repairs and publishing", Fx.primary("Mark collected", this::collect), Fx.button("Change plan...", this::changePlan),
                    Fx.button("Start repair...", this::startRefurbish), Fx.button("Repair done", this::completeRefurbish), Fx.primary("Publish to market...", this::publish),
                    Fx.button("Refresh", this::refresh)));
            setCenter(table);
        }

        @Override void refresh() {
            Fx.run(() -> {
                java.util.List<Map<String, Object>> keep = new java.util.ArrayList<>();
                for (Map<String, Object> m : ApiClient.list(app.api.get("/api/admin/items"))) {
                    String st = Ui.s(m.get("status"));
                    if (st.equals("PICKUP_SCHEDULED") || st.equals("COLLECTED") || st.equals("REFURBISHING")) keep.add(m);
                }
                return keep;
            }, table::setRows);
        }

        private void post(String path, Object body, String okMsg) {
            Fx.run(() -> app.api.post(path, body), r -> { if (okMsg != null) Fx.info(okMsg); refresh(); }, this::refresh);
        }

        private void collect() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            Fx.run(() -> ApiClient.obj(app.api.post("/api/admin/items/" + m.get("id") + "/collect", null)),
                    r -> { Fx.info(r.get("pointsAwarded") + " reward points awarded to the owner."); refresh(); }, this::refresh);
        }

        private void changePlan() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            ComboBox<Fx.Opt> plan = Fx.combo(app.decisions(), null);
            if (Fx.dialog("New plan for #" + m.get("id"), plan)) post("/api/admin/items/" + m.get("id") + "/decision", ApiClient.map("decision", Fx.sel(plan)), null);
        }

        private void startRefurbish() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            String cost = Fx.ask("Start repair", "Estimated repair cost:", "0");
            if (cost == null) return;
            try { post("/api/admin/items/" + m.get("id") + "/refurbish/start", ApiClient.map("repairCost", number(cost)), null); }
            catch (NumberFormatException e) { Fx.info("Enter a number."); }
        }

        private void completeRefurbish() {
            Map<String, Object> m = need(table);
            if (m != null) post("/api/admin/items/" + m.get("id") + "/refurbish/complete", null, "Refurbished. The item can now be published.");
        }

        private void publish() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            String price = Fx.ask("Publish", "Market price (suggested " + Ui.money(m.get("estimatedValue")) + "):", String.format("%.2f", Ui.d(m.get("estimatedValue"))));
            if (price == null) return;
            try { post("/api/admin/listings", ApiClient.map("itemId", m.get("id"), "price", number(price)), "Published. All users have been notified."); }
            catch (NumberFormatException e) { Fx.info("Enter a number."); }
        }
    }

    // ---- market listings ------------------------------------------------------------------------------
    static final class Listings extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("Listing", m -> m.get("id")).col("Item ID", m -> m.get("itemId")).key("Item", "itemName")
                .key("Category", "categoryLabel").key("Condition", "conditionLabel").col("Price", m -> Ui.money(m.get("price")));

        Listings(App app) {
            super(app, "Market listings");
            setTop(header("Items currently for sale", Fx.button("Change price...", this::price), Fx.button("Withdraw", this::withdraw), Fx.button("Refresh", this::refresh)));
            setCenter(table);
        }

        @Override void refresh() { Fx.run(() -> ApiClient.list(app.api.get("/api/market")), table::setRows); }

        private void price() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            String p = Fx.ask("Change price", "New price for " + m.get("itemName") + ":", String.format("%.2f", Ui.d(m.get("price"))));
            if (p == null) return;
            try {
                double v = number(p);
                Fx.run(() -> app.api.put("/api/admin/listings/" + m.get("id"), ApiClient.map("price", v)), r -> refresh());
            } catch (NumberFormatException e) { Fx.info("Enter a number."); }
        }

        private void withdraw() {
            Map<String, Object> m = need(table);
            if (m != null && Fx.confirm("Withdraw " + m.get("itemName") + " from the market?"))
                Fx.run(() -> app.api.delete("/api/admin/listings/" + m.get("id")), r -> refresh());
        }
    }

    // ---- recycling ---------------------------------------------------------------------------------------
    static final class Recycling extends FxPage {
        private final Fx.Kpi eligible = new Fx.Kpi("collected, waiting to be queued"), regular = new Fx.Kpi("queued - regular e-waste"), hazardous = new Fx.Kpi("queued - hazardous (batteries)");
        private final Fx.Grid batches = new Fx.Grid().col("Batch", m -> m.get("id")).col("Date", m -> Ui.date(m.get("createdAt"))).key("Partner", "partner")
                .col("Hazardous", m -> Boolean.TRUE.equals(m.get("certified")) ? "certified" : "no").col("Items", m -> m.get("itemCount"))
                .col("Kg", m -> String.format("%.1f", Ui.d(m.get("totalKg")))).col("Metal", m -> String.format("%.1f", Ui.d(m.get("metalKg"))))
                .col("Plastic", m -> String.format("%.1f", Ui.d(m.get("plasticKg")))).col("Glass", m -> String.format("%.1f", Ui.d(m.get("glassKg"))));

        Recycling(App app) {
            super(app, "Recycling");
            HBox cards = new HBox(10, eligible, regular, hazardous);
            for (Fx.Kpi k : new Fx.Kpi[]{eligible, regular, hazardous}) HBox.setHgrow(k, Priority.ALWAYS);
            VBox north = new VBox(8, header("Recycling queue & shipments", Fx.button("Refresh", this::refresh)), cards,
                    Fx.row(Fx.button("Queue all recycle-planned items", this::queueAll), Fx.primary("Ship regular batch...", () -> ship(false)), Fx.primary("Ship hazardous batch...", () -> ship(true))),
                    Fx.heading("Batch history"));
            setTop(north);
            setCenter(batches);
            setBottom(Fx.muted("Hazardous items can only be shipped to a certified hazardous-waste partner, so they never mix with regular e-waste."));
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.obj(app.api.get("/api/admin/recycling")), m -> {
                eligible.set(Ui.s(m.get("eligible")));
                regular.set(Ui.s(m.get("queuedRegular")));
                hazardous.set(Ui.s(m.get("queuedHazardous")));
                batches.setRows(ApiClient.list(m.get("batches")));
            });
        }

        private void queueAll() {
            Fx.run(() -> ApiClient.obj(app.api.post("/api/admin/recycling/queue-all", null)), r -> { Fx.info(r.get("queued") + " item(s) queued."); refresh(); });
        }

        private void ship(boolean haz) {
            String partner = Fx.ask(haz ? "Ship hazardous batch" : "Ship regular batch", haz ? "Certified hazardous-waste partner:" : "Recycling partner:", "");
            if (partner == null || partner.isBlank()) return;
            Fx.run(() -> ApiClient.obj(app.api.post("/api/admin/recycling/batches", ApiClient.map("partner", partner.trim(), "hazardous", haz))),
                    r -> { Fx.info("Batch #" + r.get("id") + " shipped (" + String.format("%.1f", Ui.d(r.get("totalKg"))) + " kg)."); refresh(); });
        }
    }

    // ---- drives ----------------------------------------------------------------------------------------------
    static final class Drives extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("ID", m -> m.get("id")).key("Drive", "name").key("Status", "status").key("Ends", "endDate")
                .col("Goal (kg)", m -> String.format("%.0f", Ui.d(m.get("goalKg")))).col("Collected (kg)", m -> String.format("%.1f", Ui.d(m.get("progressKg"))))
                .col("Progress", m -> String.format("%.0f%%", Ui.d(m.get("percent")))).col("Contributors", m -> m.get("contributors"));

        Drives(App app) {
            super(app, "Drives");
            setTop(header("Collection drives", Fx.primary("New drive...", this::create), Fx.button("End selected", this::end), Fx.button("Refresh", this::refresh)));
            setCenter(table);
            setBottom(Fx.muted("Creating a drive notifies every user (in-app and by email). Progress counts items submitted while the drive runs."));
        }

        @Override void refresh() { Fx.run(() -> ApiClient.list(app.api.get("/api/admin/campaigns")), table::setRows); }

        private void create() {
            TextField name = new TextField(), desc = new TextField(), goal = new TextField("100"), end = new TextField(LocalDate.now().plusDays(30).toString());
            if (!Fx.dialog("New collection drive", Fx.form("Name *", name, "Description", desc, "Goal (kg) *", goal, "Ends (yyyy-mm-dd) *", end))) return;
            double g;
            try { g = number(goal.getText()); } catch (NumberFormatException e) { Fx.info("Goal must be a number of kilograms."); return; }
            Map<String, Object> body = ApiClient.map("name", name.getText().trim(), "description", desc.getText().trim(), "goalKg", g, "endDate", end.getText().trim());
            Fx.run(() -> app.api.post("/api/admin/campaigns", body), r -> refresh());
        }

        private void end() {
            Map<String, Object> m = need(table);
            if (m != null && Fx.confirm("End the drive \"" + m.get("name") + "\"?"))
                Fx.run(() -> app.api.post("/api/admin/campaigns/" + m.get("id") + "/end", ApiClient.map()), r -> refresh());
        }
    }

    // ---- all items -----------------------------------------------------------------------------------------------
    static final class AllItems extends FxPage {
        private final TextField search = new TextField();
        private final ComboBox<Fx.Opt> status;
        private final Fx.Grid table = new Fx.Grid().col("ID", m -> m.get("id")).key("Owner", "ownerName").key("Item", "name").key("Tracking", "trackingCode")
                .key("Category", "categoryLabel").col("Age", m -> m.get("ageYears")).col("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg"))))
                .key("Condition", "conditionLabel").key("Status", "statusLabel").key("Plan", "decision");
        private final TextArea detail = Fx.readOnlyArea(9);

        AllItems(App app) {
            super(app, "All items");
            status = Fx.combo(app.statuses(), "Any status");
            search.setPromptText("name / brand / owner / tracking code");
            search.setPrefColumnCount(24);
            search.setOnAction(e -> refresh());
            SplitPane split = new SplitPane(table, detail);
            split.setOrientation(javafx.geometry.Orientation.VERTICAL);
            split.setDividerPositions(0.6);
            setTop(new VBox(4, Fx.heading("Search all items"), Fx.row(search, status, Fx.primary("Search", this::refresh), Fx.button("Export CSV...", this::export))));
            setCenter(split);
            table.onSelect(m -> {
                if (m == null) return;
                Fx.run(() -> ApiClient.obj(app.api.get("/api/items/" + m.get("id"))), full -> detail.setText(Ui.itemDetail(full)));
            });
        }

        @Override void refresh() {
            String q = "/api/admin/items?status=" + Fx.enc(Fx.sel(status)) + "&q=" + Fx.enc(search.getText().trim());
            Fx.run(() -> ApiClient.list(app.api.get(q)), table::setRows);
        }

        private void export() {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setInitialFileName("items.csv");
            java.io.File f = fc.showSaveDialog(getScene().getWindow());
            if (f == null) return;
            Fx.run(() -> {
                java.nio.file.Files.write(f.toPath(), app.api.getText("/api/admin/export").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return f;
            }, saved -> Fx.info("Saved " + saved.getName() + "."));
        }
    }

    // ---- users --------------------------------------------------------------------------------------------------------
    static final class Users extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("ID", m -> m.get("id")).key("Username", "username").key("Name", "fullName").key("Email", "email").key("Phone", "phone")
                .col("Points", m -> m.get("points")).col("Items", m -> m.get("itemCount")).key("Role", "role")
                .col("Active", m -> Boolean.TRUE.equals(m.get("active")) ? "yes" : "NO");

        Users(App app) {
            super(app, "Users");
            setTop(header("Registered users", Fx.button("Activate / deactivate", this::toggle), Fx.button("Toggle recycler role", this::toggleRecycler),
                    Fx.primary("Award bonus points...", this::award), Fx.button("Refresh", this::refresh)));
            setCenter(table);
        }

        @Override void refresh() { Fx.run(() -> ApiClient.list(app.api.get("/api/admin/users")), table::setRows); }

        private void toggle() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            boolean now = Boolean.TRUE.equals(m.get("active"));
            if (Fx.confirm((now ? "Deactivate " : "Reactivate ") + m.get("username") + "?"))
                Fx.run(() -> app.api.post("/api/admin/users/" + m.get("id") + "/active", ApiClient.map("active", !now)), r -> refresh());
        }

        private void award() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            String p = Fx.ask("Bonus points", "Bonus points for " + m.get("username") + " (1-10000):", "50");
            if (p == null) return;
            try {
                int v = Integer.parseInt(p.trim());
                Fx.run(() -> app.api.post("/api/admin/users/" + m.get("id") + "/points", ApiClient.map("points", v)), r -> refresh());
            } catch (NumberFormatException e) { Fx.info("Enter a whole number."); }
        }

        private void toggleRecycler() {
            Map<String, Object> m = need(table);
            if (m == null) return;
            String role = "RECYCLER".equals(m.get("role")) ? "USER" : "RECYCLER";
            String action = "RECYCLER".equals(role) ? "grant recycler access to " : "remove recycler access from ";
            if (Fx.confirm("Do you want to " + action + m.get("username") + "?"))
                Fx.run(() -> app.api.post("/api/admin/users/" + m.get("id") + "/role", ApiClient.map("role", role)),
                        result -> refresh());
        }
    }

    // ---- announcements & email ------------------------------------------------------------------------------------------
    static final class Announce extends FxPage {
        private final TextArea message = new TextArea();
        private final Label mailState = Fx.muted(" "), mailStats = Fx.muted(" ");
        private final Button test = Fx.button("Send test email...", this::sendTest);

        Announce(App app) {
            super(app, "Announcements & email");
            message.setWrapText(true);
            message.setPrefRowCount(6);
            VBox ann = new VBox(6, new Label("Announcement to every active user"),
                    Fx.muted("Shown in their Notifications tab and emailed to users who have email notifications on."), message, new HBox(Fx.primary("Send to all users", this::send)));
            VBox mail = new VBox(6, new Label("Email delivery"), mailState, mailStats, Fx.row(test, Fx.button("Refresh status", this::refresh)));
            for (VBox b : new VBox[]{ann, mail}) { b.setStyle("-fx-border-color:#CFD8DC;"); b.setPadding(new javafx.geometry.Insets(10)); b.setMaxWidth(640); }
            setTop(header("Announcements & email"));
            setCenter(new VBox(12, ann, mail));
        }

        private void send() {
            String text = message.getText().trim();
            if (text.isEmpty()) { Fx.info("Write a message first."); return; }
            Fx.run(() -> ApiClient.obj(app.api.post("/api/admin/announce", ApiClient.map("message", text))), r -> {
                Fx.info("Sent to " + r.get("sent") + " user(s).");
                message.clear();
                refresh();
            });
        }

        private void sendTest() {
            String to = Fx.ask("Test email", "Send a test email to:", Ui.s(app.user.get("email")));
            if (to == null || to.isBlank()) return;
            Fx.run(() -> app.api.post("/api/admin/mail/test", ApiClient.map("to", to.trim())),
                    r -> { Fx.info("Test email sent to " + to.trim() + ". Check the inbox (and spam folder)."); refresh(); }, this::refresh);
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.obj(app.api.get("/api/admin/mail")), m -> {
                boolean on = Boolean.TRUE.equals(m.get("enabled"));
                test.setDisable(!on);
                if (!on) {
                    mailState.setText("Email is OFF. Copy config/mail.properties.example to config/mail.properties, fill it in and restart the server.");
                    mailStats.setText("Users still get every notification inside the app.");
                } else {
                    mailState.setText("Email is ON: " + m.get("host") + ":" + m.get("port") + " (" + m.get("security") + "), from " + m.get("from"));
                    String err = Ui.s(m.get("lastError"));
                    mailStats.setText("Sent: " + m.get("sent") + "   Failed: " + m.get("failed") + "   Waiting: " + m.get("queued") + (err.isEmpty() ? "" : "   Last error: " + err));
                }
            });
        }
    }
}
