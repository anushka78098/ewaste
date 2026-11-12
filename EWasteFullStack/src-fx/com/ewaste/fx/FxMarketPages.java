package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Marketplace dashboards, owner photo uploads, and buyer/seller conversations. */
final class FxMarketPages {
    private FxMarketPages() {}

    static final class Dashboard extends FxPage {
        private final boolean sellerView;
        private final Fx.Kpi first, second, third;
        private final Fx.Grid rows;

        Dashboard(App app, boolean sellerView) {
            super(app, sellerView ? "Seller dashboard" : "Buyer dashboard");
            this.sellerView = sellerView;
            if (sellerView) {
                first = new Fx.Kpi("active listings");
                second = new Fx.Kpi("items sold");
                third = new Fx.Kpi("sales to date");
                rows = new Fx.Grid().col("Listing", m -> m.get("listingId")).key("Item", "itemName")
                        .col("Price", m -> Ui.money(m.get("price")))
                        .col("Status", m -> Boolean.TRUE.equals(m.get("active")) ? "Listed" : Boolean.TRUE.equals(m.get("sold")) ? "Sold" : "Closed");
                setTop(header("Your marketplace sales", Fx.button("Upload photo...", this::uploadPhoto), Fx.button("Refresh", this::refresh)));
            } else {
                first = new Fx.Kpi("orders placed");
                second = new Fx.Kpi("total spent");
                third = new Fx.Kpi("market savings");
                rows = new Fx.Grid().col("Order", m -> m.get("orderId")).key("Item", "itemName").key("Seller", "sellerName")
                        .col("Paid", m -> Ui.money(m.get("amountPaid"))).col("Date", m -> Ui.date(m.get("createdAt")));
                setTop(header("Your marketplace purchases", Fx.button("Refresh", this::refresh)));
            }
            HBox cards = new HBox(10, first, second, third);
            for (Region card : new Region[]{first, second, third}) HBox.setHgrow(card, Priority.ALWAYS);
            VBox body = new VBox(10, cards, rows);
            body.setPadding(new Insets(4, 0, 0, 0));
            setCenter(body);
            VBox.setVgrow(rows, Priority.ALWAYS);
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.obj(app.api.get("/api/me/market/dashboard")), data -> {
                Map<String, Object> d = ApiClient.obj(data.get(sellerView ? "seller" : "buyer"));
                if (sellerView) {
                    first.set(String.valueOf(Ui.i(d.get("activeListings"))));
                    second.set(String.valueOf(Ui.i(d.get("soldItems"))));
                    third.set(Ui.money(d.get("salesTotal")));
                    rows.setRows(ApiClient.list(d.get("items")));
                } else {
                    first.set(String.valueOf(Ui.i(d.get("orders"))));
                    second.set(Ui.money(d.get("totalSpent")));
                    double savings = 0;
                    List<Map<String, Object>> purchases = ApiClient.list(d.get("recent"));
                    for (Map<String, Object> o : purchases) savings += Ui.d(o.get("discount"));
                    third.set(Ui.money(savings));
                    rows.setRows(purchases);
                }
            });
        }

        private void uploadPhoto() {
            Map<String, Object> selected = rows.selected();
            if (selected == null) { Fx.info("Select one of your listed items first."); return; }
            if (!Boolean.TRUE.equals(selected.get("active"))) { Fx.info("Only active listings can have their photos updated."); return; }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a PNG or JPEG product photo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG or JPEG images", "*.png", "*.jpg", "*.jpeg"));
            File file = chooser.showOpenDialog(null);
            if (file == null) return;
            Path path = file.toPath();
            Fx.run(() -> ApiClient.obj(app.api.post("/api/market/" + selected.get("listingId") + "/photo", ApiClient.filePayload(path))),
                    result -> { Fx.info("Photo uploaded."); refresh(); });
        }
    }

    static final class Messages extends FxPage {
        private final Fx.Grid conversations = new Fx.Grid().col("Listing", m -> m.get("listingId")).key("Item", "itemName")
                .key("With", "otherUserName").key("Latest message", "latestMessage");
        private final Fx.Grid messages = new Fx.Grid().col("Date", m -> Ui.date(m.get("createdAt"))).key("From", "senderName")
                .key("Message", "text").key("Attachment", "fileName");
        private final TextArea text = new TextArea();
        private final Label attached = Fx.muted("No attachment selected");
        private Integer listingId, otherUserId;
        private Path attachment;

        Messages(App app) {
            super(app, "Messages");
            text.setPrefRowCount(3);
            text.setWrapText(true);
            HBox controls = Fx.row(Fx.button("Attach file...", this::chooseAttachment),
                    Fx.primary("Send message", this::send), Fx.button("Download selected attachment", this::download));
            VBox composer = new VBox(4, attached, text, controls);
            BorderPane right = new BorderPane();
            right.setCenter(messages);
            right.setBottom(composer);
            SplitPane split = new SplitPane(conversations, right);
            split.setDividerPositions(0.35);
            setTop(header("Buyer and seller messages", Fx.button("Refresh", this::refresh)));
            setCenter(split);
            conversations.onSelect(m -> {
                if (m == null) return;
                listingId = Ui.i(m.get("listingId"));
                otherUserId = Ui.i(m.get("otherUserId"));
                loadMessages();
            });
        }

        void openConversation(int listing, int otherUser) {
            listingId = listing;
            otherUserId = otherUser;
            app.goTo.accept("Messages");
            loadMessages();
        }

        @Override void refresh() {
            Fx.run(() -> ApiClient.list(app.api.get("/api/messages")), rows -> {
                conversations.setRows(rows);
                if (listingId != null && otherUserId != null) loadMessages();
            });
        }

        private void loadMessages() {
            if (listingId == null || otherUserId == null) return;
            Fx.run(() -> ApiClient.list(app.api.get("/api/market/" + listingId + "/messages?with=" + otherUserId)), messages::setRows);
        }

        private void chooseAttachment() {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose an image or PDF (maximum 4 MB)");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG, JPEG, or PDF", "*.png", "*.jpg", "*.jpeg", "*.pdf"));
            File file = chooser.showOpenDialog(null);
            if (file == null) return;
            if (file.length() > 4_000_000) { Fx.info("Files must be 4 MB or smaller."); return; }
            attachment = file.toPath();
            attached.setText(file.getName());
        }

        private void send() {
            if (listingId == null || otherUserId == null) {
                Fx.info("Select a conversation, or choose a listing and use Message seller from the Market tab.");
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text.getText().trim());
            body.put("recipientId", otherUserId);
            if (attachment != null) {
                try { body.putAll(ApiClient.filePayload(attachment)); }
                catch (java.io.IOException e) { Fx.error(e); return; }
            }
            Fx.run(() -> ApiClient.obj(app.api.post("/api/market/" + listingId + "/messages", body)), result -> {
                text.clear();
                attachment = null;
                attached.setText("No attachment selected");
                refresh();
            });
        }

        private void download() {
            Map<String, Object> selected = messages.selected();
            if (selected == null || Ui.s(selected.get("downloadUrl")).isEmpty()) {
                Fx.info("Select a message with an attachment first.");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName(Ui.s(selected.get("fileName")));
            File target = chooser.showSaveDialog(null);
            if (target == null) return;
            Fx.run(() -> {
                Files.write(target.toPath(), app.api.getBytes(Ui.s(selected.get("downloadUrl"))));
                return target;
            }, file -> Fx.info("Saved " + file.getName() + "."));
        }
    }
}
