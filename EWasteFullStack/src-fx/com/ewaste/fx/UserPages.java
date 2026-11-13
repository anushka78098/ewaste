package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Screens for a normal user: submit, track, buy, orders, notifications, impact, profile. */
final class UserPages {
    private UserPages() {}

    static List<FxPage> create(App app) {
        return Arrays.asList(new FxDashboards.User(app), new FxMarketPages.Dashboard(app, false), new FxMarketPages.Dashboard(app, true),
                new Submit(app), new MyItems(app), new Market(app), new FxMarketPages.Messages(app), new FxSupportPage(app, false), new Orders(app),
                new EcoChallenges(app, false), new Notifications(app), new Impact(app), new Profile(app));
    }

    // ---- eco challenges -------------------------------------------------------------------------
    static final class EcoChallenges extends FxPage {
        private final boolean admin;
        private final VBox cards = new VBox(8);
        private final VBox badges = new VBox(6);
        private final ComboBox<String> period = new ComboBox<>();
        private final Fx.Grid leaderboard = new Fx.Grid().col("Rank", m -> m.get("rank")).key("Participant", "name")
                .col("Bonus points", m -> m.get("points")).col("Challenges", m -> m.get("completedChallenges"));
        private Map<String, Object> userData;

        EcoChallenges(App app, boolean admin) {
            super(app, "Eco challenges");
            this.admin = admin;
            period.getItems().addAll("WEEKLY", "MONTHLY");
            period.setValue("WEEKLY");
            period.setOnAction(e -> loadLeaderboard());

            ScrollPane challengeScroll = new ScrollPane(cards);
            challengeScroll.setFitToWidth(true);
            VBox left = new VBox(8, Fx.heading(admin ? "Manage eco challenges" : "Join an eco challenge"), challengeScroll);
            VBox right = new VBox(8);
            if (!admin) right.getChildren().addAll(Fx.heading("Achievements"), badges);
            right.getChildren().addAll(Fx.heading("Challenge leaderboard"), new HBox(8, Fx.muted("Period"), period), leaderboard);
            SplitPane split = new SplitPane(left, right);
            split.setDividerPositions(0.58);
            setTop(header("Eco challenges", Fx.button("Refresh", this::refresh),
                    admin ? Fx.primary("Create challenge...", this::createChallenge) : Fx.muted("Earn bonus points by completing verified activities.")));
            setCenter(split);
        }

        @Override void refresh() {
            String path = admin ? "/api/admin/eco/challenges" : "/api/eco/challenges";
            Fx.run(() -> admin ? ApiClient.list(app.api.get(path)) : ApiClient.obj(app.api.get(path)), result -> {
                if (admin) renderCards(ApiClient.list(result));
                else {
                    Map<String, Object> data = ApiClient.obj(result);
                    userData = data;
                    renderCards(ApiClient.list(data.get("challenges")));
                    renderBadges(ApiClient.list(data.get("achievements")));
                    showLeaderboard(data);
                }
                if (admin) loadLeaderboard();
            });
        }

        private void renderCards(List<Map<String, Object>> challenges) {
            cards.getChildren().clear();
            if (challenges.isEmpty()) cards.getChildren().add(Fx.muted("No eco challenges are available right now."));
            for (Map<String, Object> challenge : challenges) {
                Label title = Fx.heading(Ui.s(challenge.get("title")) + "  -  " + Ui.s(challenge.get("period"))
                        + "  |  +" + challenge.get("rewardPoints") + " points");
                Label detail = Fx.muted(Ui.s(challenge.get("description")) + "  |  "
                        + Ui.s(challenge.get("targetDescription")) + "  |  ends " + Ui.s(challenge.get("endDate"))
                        + "  |  " + Ui.s(challenge.get("status")));
                ProgressBar progress = new ProgressBar(Math.max(0, Math.min(1, Ui.d(challenge.get("percent")) / 100.0)));
                progress.setMaxWidth(Double.MAX_VALUE);
                Label value = Fx.muted(String.format("%.1f / %.1f", Ui.d(challenge.get("progress")), Ui.d(challenge.get("targetValue"))));
                HBox actions = new HBox(8);
                if (Boolean.TRUE.equals(challenge.get("canEnroll"))) {
                    actions.getChildren().add(Fx.primary("Join challenge", () -> enroll(challenge)));
                } else if (admin && "ACTIVE".equals(challenge.get("status"))) {
                    actions.getChildren().add(Fx.button("End early", () -> endChallenge(challenge)));
                } else if (admin) {
                    actions.getChildren().add(Fx.muted(challenge.get("enrollmentCount") + " enrolled / "
                            + challenge.get("completionCount") + " completed"));
                } else if (Boolean.TRUE.equals(challenge.get("enrolled"))) {
                    actions.getChildren().add(Fx.muted("Enrolled"));
                }
                VBox card = new VBox(6, title, detail, progress, value, actions);
                card.setPadding(new Insets(10));
                card.setStyle("-fx-background-color:#F1F8E9;-fx-border-color:#B7D7B9;-fx-border-radius:4;");
                cards.getChildren().add(card);
            }
        }

        private void renderBadges(List<Map<String, Object>> earned) {
            badges.getChildren().clear();
            if (earned.isEmpty()) badges.getChildren().add(Fx.muted("No achievements earned yet."));
            for (Map<String, Object> badge : earned)
                badges.getChildren().add(Fx.muted("★  " + Ui.s(badge.get("label")) + " - " + Ui.s(badge.get("description"))));
        }

        private void showLeaderboard(Map<String, Object> data) {
            String key = "MONTHLY".equals(period.getValue()) ? "monthlyLeaderboard" : "weeklyLeaderboard";
            leaderboard.setRows(ApiClient.list(data.get(key)));
        }

        private void loadLeaderboard() {
            if (!admin) {
                if (userData != null) showLeaderboard(userData);
                return;
            }
            Fx.run(() -> ApiClient.list(app.api.get("/api/eco/leaderboard?period=" + period.getValue())), leaderboard::setRows);
        }

        private void enroll(Map<String, Object> challenge) {
            Fx.run(() -> app.api.post("/api/eco/challenges/" + challenge.get("id") + "/enroll", ApiClient.map()),
                    result -> refresh());
        }

        private void createChallenge() {
            TextField title = new TextField(), description = new TextField();
            ComboBox<String> type = new ComboBox<>();
            type.getItems().addAll("DONATE_KG", "DONATE_REUSABLE", "VERIFIED_RECYCLING", "COLLECTION_DRIVE");
            type.setValue("DONATE_KG");
            ComboBox<String> cadence = new ComboBox<>();
            cadence.getItems().addAll("WEEKLY", "MONTHLY");
            cadence.setValue("WEEKLY");
            TextField target = new TextField("5"), points = new TextField("100");
            if (!Fx.dialog("Create eco challenge", Fx.form("Title *", title, "Description", description,
                    "Type", type, "Cadence", cadence, "Target *", target, "Bonus points *", points))) return;
            try {
                Map<String, Object> body = ApiClient.map("title", title.getText().trim(), "description", description.getText().trim(),
                        "type", type.getValue(), "period", cadence.getValue(),
                        "targetValue", Double.parseDouble(target.getText().trim()),
                        "rewardPoints", Integer.parseInt(points.getText().trim()));
                Fx.run(() -> app.api.post("/api/admin/eco/challenges", body), result -> refresh());
            } catch (NumberFormatException e) {
                Fx.info("Target and bonus points must be valid numbers.");
            }
        }

        private void endChallenge(Map<String, Object> challenge) {
            if (Fx.confirm("End \"" + challenge.get("title") + "\" now?"))
                Fx.run(() -> app.api.post("/api/admin/eco/challenges/" + challenge.get("id") + "/end", ApiClient.map()),
                        result -> refresh());
        }
    }

    // ---- submit ---------------------------------------------------------------------------------
    static final class Submit extends FxPage {
        private final TextField name = new TextField(), brand = new TextField(), age = new TextField("3"), weight = new TextField("1.0");
        private final ComboBox<Fx.Opt> category, condition;
        private final TextArea description = new TextArea();
        private final Label hint = Fx.muted(" ");

        Submit(App app) {
            super(app, "Submit item");
            category = Fx.combo(app.categories(), null);
            condition = Fx.combo(app.conditions(), null);
            description.setPrefRowCount(3);
            description.setWrapText(true);
            category.setOnAction(e -> updateHint());
            VBox box = new VBox(10, Fx.form("Item name *", name, "Brand", brand, "Category", category, "Age (years)", age, "Weight (kg)", weight,
                    "Condition", condition, "Description", description), hint, new HBox(Fx.primary("Submit for review", this::submit)),
                    Fx.muted("An admin reviews each submission and decides whether it is reused, repaired or recycled. You earn reward points once it is collected."));
            box.setMaxWidth(560);
            setTop(header("Hand in an electronic item"));
            setCenter(box);
            updateHint();
        }

        private void updateHint() {
            String sel = Fx.sel(category);
            for (Map<String, Object> c : app.categories()) {
                if (Ui.s(c.get("name")).equals(sel))
                    hint.setText("Reuse age limit: " + c.get("maxReuseAge") + " yrs  |  " + c.get("pointsPerKg") + " points per kg"
                            + (Boolean.TRUE.equals(c.get("hazardous")) ? "  |  Hazardous - keep dry, do not puncture" : ""));
            }
        }

        private void submit() {
            if (name.getText().trim().isEmpty()) { Fx.info("Please enter the item name."); return; }
            int a;
            double w;
            try {
                a = Integer.parseInt(age.getText().trim());
                w = Double.parseDouble(weight.getText().trim());
            } catch (NumberFormatException e) { Fx.info("Age must be a whole number and weight a number (e.g. 1.5)."); return; }
            Map<String, Object> body = ApiClient.map("name", name.getText().trim(), "brand", brand.getText().trim(), "category", Fx.sel(category),
                    "ageYears", a, "weightKg", w, "condition", Fx.sel(condition), "description", description.getText().trim());
            Fx.run(() -> ApiClient.obj(app.api.post("/api/items", body)), it -> {
                Map<String, Object> report = ApiClient.obj(it.get("decisionReport"));
                Fx.text("Explainable decision report", "Submitted! Item #" + it.get("id") + " (tracking code "
                        + it.get("trackingCode") + ") is awaiting admin review.\n\n" + Ui.decisionReport(report));
                name.clear(); brand.clear(); description.clear();
                app.refreshHeader.run();
            });
        }

        @Override void refresh() { }
    }

    // ---- my items ---------------------------------------------------------------------------------
    static final class MyItems extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("ID", m -> m.get("id")).key("Item", "name").key("Tracking", "trackingCode").key("Category", "categoryLabel")
                .col("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))).key("Status", "statusLabel")
                .col("Plan", m -> m.get("decision") != null ? m.get("decision") : "(suggested: " + m.get("suggestedDecision") + ")").col("Pickup", m -> Ui.s(m.get("pickupDate")));
        private final TextArea detail = Fx.readOnlyArea(10);

        MyItems(App app) {
            super(app, "My items");
            detail.setText("Select an item to see its full timeline.");
            SplitPane split = new SplitPane(table, detail);
            split.setOrientation(javafx.geometry.Orientation.VERTICAL);
            split.setDividerPositions(0.55);
            setTop(header("My items and their progress", Fx.button("Refresh", this::refresh)));
            setCenter(split);
            table.onSelect(m -> {
                if (m == null) return;
                Fx.run(() -> ApiClient.obj(app.api.get("/api/items/" + m.get("id"))), full -> detail.setText(Ui.itemDetail(full)));
            });
        }

        @Override void refresh() { Fx.run(() -> ApiClient.list(app.api.get("/api/items/mine")), table::setRows); }
    }

    // ---- market -------------------------------------------------------------------------------------
    static final class Market extends FxPage {
        private final TextField keyword = new TextField(), maxPrice = new TextField();
        private final ComboBox<Fx.Opt> category;
        private final Fx.Grid table = new Fx.Grid().col("Listing", m -> m.get("id"))
                .col("Item", m -> Ui.s(m.get("itemName")) + (Ui.s(m.get("brand")).isEmpty() ? "" : " - " + m.get("brand")))
                .key("Category", "categoryLabel").key("Condition", "conditionLabel").col("Age", m -> m.get("ageYears") + " y")
                .col("Circularity", m -> m.get("circularityScore") + "/100").col("Price", m -> Ui.money(m.get("price")));
        private final TextArea detail = Fx.readOnlyArea(4);
        private final ImageView photo = new ImageView();

        Market(App app) {
            super(app, "Market");
            category = Fx.combo(app.categories(), "Any category");
            keyword.setPromptText("keyword");
            maxPrice.setPromptText("max price");
            keyword.setOnAction(e -> refresh());
            maxPrice.setOnAction(e -> refresh());
            VBox north = new VBox(4, Fx.heading("Refurbished and reusable items"),
                    Fx.row(keyword, category, maxPrice, Fx.primary("Search", this::refresh),
                            Fx.button("Circularity passport...", this::showPassport), Fx.button("Message seller...", this::messageSeller),
                            Fx.primary("Buy selected...", this::buy)));
            setTop(north);
            setCenter(table);
            photo.setFitWidth(180);
            photo.setFitHeight(130);
            photo.setPreserveRatio(true);
            HBox lower = new HBox(10, detail, photo);
            setBottom(lower);
            table.onSelect(m -> {
                detail.setText(m == null ? "" : Ui.s(m.get("itemName")) + "\nSeller: " + Ui.s(m.get("sellerName"))
                        + "\n" + (Ui.s(m.get("description")).isEmpty() ? "No description." : m.get("description")));
                photo.setImage(null);
                if (m != null && !Ui.s(m.get("photoUrl")).isEmpty())
                    Fx.run(() -> new Image(new ByteArrayInputStream(app.api.getBytes(Ui.s(m.get("photoUrl"))))), photo::setImage);
            });
        }

        @Override void refresh() {
            String q = "/api/market?q=" + Fx.enc(keyword.getText().trim()) + "&category=" + Fx.enc(Fx.sel(category)) + "&maxPrice=" + Fx.enc(maxPrice.getText().trim());
            Fx.run(() -> ApiClient.list(app.api.get(q)), table::setRows);
        }

        private void buy() {
            Map<String, Object> l = table.selected();
            if (l == null) { Fx.info("Select an item first."); return; }
            double price = Ui.d(l.get("price"));
            int have = Ui.i(app.user.get("points"));
            int max = Math.max(0, Math.min(have, (int) Math.floor(price * app.maxPointDiscount() / app.pointValue())));
            TextField pts = new TextField("0");
            Label total = new Label();
            Runnable upd = () -> {
                int v = 0;
                try { v = Integer.parseInt(pts.getText().trim()); } catch (NumberFormatException ignored) { }
                total.setText("You pay: " + Ui.money(price - v * app.pointValue()) + "  (pay on delivery)");
            };
            pts.textProperty().addListener((o, a, b) -> upd.run());
            upd.run();
            if (!Fx.dialog("Buy " + l.get("itemName"), Fx.form("Item", new Label(Ui.s(l.get("itemName"))), "Price", new Label(Ui.money(price)),
                    "Your points", new Label(have + "  (1 point = " + Ui.money(app.pointValue()) + ", max " + (int) (app.maxPointDiscount() * 100) + "% off; you can use up to " + max + ")"),
                    "Redeem points", pts, "", total))) return;
            int use;
            try { use = Integer.parseInt(pts.getText().trim()); } catch (NumberFormatException e) { Fx.info("Points must be a whole number."); return; }
            Fx.run(() -> ApiClient.obj(app.api.post("/api/market/" + l.get("id") + "/buy", ApiClient.map("points", use))), o -> {
                Fx.info("Order #" + o.get("id") + " placed. You pay " + Ui.money(o.get("amountPaid")) + " on delivery.");
                app.refreshHeader.run();
                refresh();
            }, this::refresh);
        }

        private void messageSeller() {
            Map<String, Object> selected = table.selected();
            if (selected == null) { Fx.info("Select a listing first."); return; }
            int ownerId = Ui.i(selected.get("ownerId"));
            if (ownerId == Ui.i(app.user.get("id"))) { Fx.info("This is your own listing."); return; }
            app.openConversation.accept(Ui.i(selected.get("id")), ownerId);
        }

        private void showPassport() {
            Map<String, Object> selected = table.selected();
            if (selected == null) { Fx.info("Select a listing first."); return; }
            Fx.run(() -> ApiClient.obj(app.api.get("/api/passport/" + Ui.s(selected.get("trackingCode")))), passport -> {
                StringBuilder report = new StringBuilder();
                report.append(Ui.s(passport.get("itemName"))).append(" - ").append(Ui.s(passport.get("categoryLabel")))
                        .append("\nCircularity score: ").append(passport.get("circularityScore")).append("/100 - ").append(passport.get("scoreLabel"))
                        .append("\nTracking code: ").append(passport.get("trackingCode"))
                        .append("\nCondition: ").append(passport.get("conditionLabel")).append(" | Age: ").append(passport.get("ageYears"))
                        .append(" years | Weight: ").append(Ui.kg(passport.get("weightKg")))
                        .append("\n\nEstimated CO2 avoided: ").append(Ui.kg(passport.get("co2AvoidedKg")))
                        .append("\nPotential if reused: ").append(Ui.kg(passport.get("co2PotentialKg")))
                        .append("\n\nSCORE BREAKDOWN\n");
                for (Map<String, Object> f : ApiClient.list(passport.get("scoreFactors")))
                    report.append("- ").append(f.get("name")).append(": ").append(f.get("score")).append('/').append(f.get("max"))
                            .append(" - ").append(f.get("explanation")).append('\n');
                Map<String, Object> materials = ApiClient.obj(passport.get("materialsKg"));
                report.append("\nESTIMATED MATERIAL CONTENT\nMetal ").append(Ui.kg(materials.get("metalKg")))
                        .append(" | Plastic ").append(Ui.kg(materials.get("plasticKg")))
                        .append(" | Glass ").append(Ui.kg(materials.get("glassKg")))
                        .append(" | Hazardous ").append(Ui.kg(materials.get("hazardousKg")))
                        .append(" | Other ").append(Ui.kg(materials.get("otherKg"))).append("\n\nJOURNEY\n");
                for (Map<String, Object> step : ApiClient.list(passport.get("journey")))
                    report.append(Boolean.TRUE.equals(step.get("complete")) ? "[x] " : "[ ] ").append(step.get("label")).append('\n');
                report.append("\n").append(passport.get("impactMethod")).append("\n").append(passport.get("privacyNote"));
                Fx.text("Circularity Passport", report.toString());
            });
        }
    }

    // ---- orders -----------------------------------------------------------------------------------
    static final class Orders extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("Order", m -> m.get("id")).key("Item", "itemName").col("List price", m -> Ui.money(m.get("listPrice")))
                .col("Points used", m -> m.get("pointsUsed")).col("Paid", m -> Ui.money(m.get("amountPaid"))).col("Date", m -> Ui.date(m.get("createdAt")));

        Orders(App app) {
            super(app, "My orders");
            setTop(header("My orders", Fx.button("Refresh", this::refresh)));
            setCenter(table);
        }

        @Override void refresh() { Fx.run(() -> ApiClient.list(app.api.get("/api/orders/mine")), table::setRows); }
    }

    // ---- notifications -----------------------------------------------------------------------------
    static final class Notifications extends FxPage {
        private final Fx.Grid table = new Fx.Grid().col("", m -> Boolean.TRUE.equals(m.get("read")) ? "" : "NEW").col("Date", m -> Ui.date(m.get("createdAt"))).key("Message", "message");

        Notifications(App app) {
            super(app, "Notifications");
            setTop(header("Notifications", Fx.button("Refresh", this::refresh)));
            setCenter(table);
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.list(app.api.get("/api/notifications")), list -> {
                table.setRows(list);
                boolean anyUnread = false;
                for (Map<String, Object> n : list) if (!Boolean.TRUE.equals(n.get("read"))) anyUnread = true;
                if (anyUnread) Fx.run(() -> app.api.post("/api/notifications/read", null), r -> app.refreshHeader.run());
            });
        }
    }

    // ---- impact --------------------------------------------------------------------------------------
    static final class Impact extends FxPage {
        private final Fx.Kpi points = new Fx.Kpi("reward points (redeemable in the market)"), kg = new Fx.Kpi("of e-waste handed in"), co2 = new Fx.Kpi("CO2 emissions avoided");
        private final Fx.Grid board = new Fx.Grid().col("Rank", m -> m.get("rank")).key("User", "fullName")
                .col("kg donated", m -> String.format("%.1f", Ui.d(m.get("kgDonated")))).col("Points", m -> m.get("points"));
        private final Fx.Grid guide = new Fx.Grid().key("Category", "label").col("Max reuse age", m -> m.get("maxReuseAge") + " yrs")
                .col("Points per kg", m -> m.get("pointsPerKg")).col("Hazardous", m -> Boolean.TRUE.equals(m.get("hazardous")) ? "yes" : "no");

        Impact(App app) {
            super(app, "Rewards & impact");
            guide.setRows(app.categories());
            HBox cards = new HBox(10, points, kg, co2);
            for (Fx.Kpi k : new Fx.Kpi[]{points, kg, co2}) HBox.setHgrow(k, Priority.ALWAYS);
            HBox lower = new HBox(12, new VBox(4, Fx.heading("Top contributors"), board), new VBox(4, Fx.heading("What earns points"), guide));
            for (javafx.scene.Node n : lower.getChildren()) { HBox.setHgrow(n, Priority.ALWAYS); VBox.setVgrow(((VBox) n).getChildren().get(1), Priority.ALWAYS); }
            VBox north = new VBox(8, header("Your impact", Fx.button("Refresh", this::refresh)), cards);
            setTop(north);
            setCenter(lower);
            setBottom(Fx.muted("Points are earned when an item is collected (+50% for reusable items) and when a donated item is sold."));
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.obj(app.api.get("/api/me/impact")), m -> {
                points.set(Ui.i(m.get("points")) + "  (" + Ui.money(m.get("pointsValue")) + ")");
                kg.set(Ui.kg(m.get("kgDonated")));
                co2.set(Ui.kg(m.get("co2Saved")));
            });
            Fx.run(() -> ApiClient.list(app.api.get("/api/leaderboard")), board::setRows);
        }
    }

    // ---- profile (shared with admin) -----------------------------------------------------------------
    static final class Profile extends FxPage {
        private final TextField fullName = new TextField(), email = new TextField(), phone = new TextField(), address = new TextField();
        private final CheckBox emailMe = new CheckBox("Email me my notifications (in-app notifications are always kept)");
        private final PasswordField oldPw = new PasswordField(), newPw = new PasswordField(), confirmPw = new PasswordField();

        Profile(App app) {
            super(app, "Profile");
            emailMe.setSelected(true);
            emailMe.setVisible(!app.isAdmin());
            emailMe.setManaged(!app.isAdmin());
            VBox contact = new VBox(8, new Label("Contact details"), Fx.form("Full name", fullName, "Email", email, "Phone", phone, "Address", address), emailMe,
                    new HBox(Fx.primary("Save details", this::saveProfile)));
            VBox pw = new VBox(8, new Label("Change password"), Fx.form("Current password", oldPw, "New password", newPw, "Confirm new", confirmPw),
                    new HBox(Fx.primary("Change password", this::changePassword)));
            for (VBox b : new VBox[]{contact, pw}) { b.setPadding(new Insets(10)); b.setStyle("-fx-border-color:#CFD8DC;"); b.setMaxWidth(560); }
            setTop(header("My profile"));
            setCenter(new VBox(12, contact, pw));
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.obj(app.api.get("/api/me")), m -> {
                fullName.setText(Ui.s(m.get("fullName")));
                email.setText(Ui.s(m.get("email")));
                phone.setText(Ui.s(m.get("phone")));
                address.setText(Ui.s(m.get("address")));
                emailMe.setSelected(!Boolean.TRUE.equals(m.get("emailOptOut")));
            });
        }

        private void saveProfile() {
            Map<String, Object> body = ApiClient.map("fullName", fullName.getText().trim(), "email", email.getText().trim(), "phone", phone.getText().trim(),
                    "address", address.getText().trim(), "emailOptOut", !emailMe.isSelected());
            Fx.run(() -> app.api.put("/api/me", body), r -> { Fx.info("Profile updated."); app.refreshHeader.run(); });
        }

        private void changePassword() {
            if (!newPw.getText().equals(confirmPw.getText())) { Fx.info("The new passwords do not match."); return; }
            Map<String, Object> body = ApiClient.map("oldPassword", oldPw.getText(), "newPassword", newPw.getText());
            Fx.run(() -> app.api.post("/api/me/password", body), r -> {
                Fx.info("Password changed.");
                oldPw.clear(); newPw.clear(); confirmPw.clear();
            });
        }
    }
}
