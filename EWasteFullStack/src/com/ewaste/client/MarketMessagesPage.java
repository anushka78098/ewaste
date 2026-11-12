package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

/** Buyer/seller messaging with optional PNG, JPEG, or PDF attachments. */
public class MarketMessagesPage extends Page {
    private final MapTable conversations = new MapTable(num("Listing", m -> m.get("listingId")), key("Item", "itemName"),
            key("Conversation with", "otherUserName"), key("Latest message", "latestMessage"));
    private final MapTable messages = new MapTable(col("Date", m -> Ui.date(m.get("createdAt"))), key("From", "senderName"),
            key("Message", "text"), key("Attachment", "fileName"));
    private final JTextArea text = new JTextArea(3, 50);
    private final JLabel attachmentName = new JLabel("No attachment selected");
    private Integer listingId, otherUserId;
    private Path attachment;

    public MarketMessagesPage(App app) {
        super(app, "Messages");
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JPanel compose = new JPanel(new BorderLayout(8, 4));
        JPanel controls = Ui.buttonRow(Ui.button("Attach file...", this::chooseAttachment),
                Ui.primary("Send message", this::send), Ui.button("Download selected attachment", this::download));
        compose.add(attachmentName, BorderLayout.NORTH);
        compose.add(new JScrollPane(text), BorderLayout.CENTER);
        compose.add(controls, BorderLayout.SOUTH);
        JScrollPane messageScroll = messages.scroll();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, conversations.scroll(), messageScroll);
        split.setResizeWeight(0.35);
        messages.setPreferredScrollableViewportSize(new Dimension(650, 400));
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.add(messageScroll, BorderLayout.CENTER);
        right.add(compose, BorderLayout.SOUTH);
        split.setRightComponent(right);
        add(top("Buyer and seller messages", Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        conversations.onSelect(m -> {
            if (m == null) return;
            listingId = Ui.i(m.get("listingId"));
            otherUserId = Ui.i(m.get("otherUserId"));
            loadMessages();
        });
    }

    public void openConversation(int listing, int otherUser) {
        listingId = listing;
        otherUserId = otherUser;
        app.goTo.accept("Messages");
        loadMessages();
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/messages")), rows -> {
            conversations.setRows(rows);
            if (listingId != null && otherUserId != null) loadMessages();
        });
    }

    private void loadMessages() {
        if (listingId == null || otherUserId == null) return;
        String path = "/api/market/" + listingId + "/messages?with=" + otherUserId;
        Ui.run(this, () -> ApiClient.list(app.api.get(path)), messages::setRows);
    }

    private void chooseAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose an image or PDF (maximum 4 MB)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath();
        String name = selected.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".pdf"))) {
            Ui.info(this, "Choose a PNG, JPEG, or PDF file.");
            return;
        }
        try {
            if (Files.size(selected) > 4_000_000) { Ui.info(this, "Files must be 4 MB or smaller."); return; }
        } catch (java.io.IOException e) { Ui.error(this, e); return; }
        attachment = selected;
        attachmentName.setText(selected.getFileName().toString());
    }

    private void send() {
        if (listingId == null || otherUserId == null) {
            Ui.info(this, "Select a conversation, or choose a listing and use Message seller from the Market tab.");
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text.getText().trim());
        body.put("recipientId", otherUserId);
        if (attachment != null) {
            try { body.putAll(ApiClient.filePayload(attachment)); }
            catch (java.io.IOException e) { Ui.error(this, e); return; }
        }
        Ui.run(this, () -> ApiClient.obj(app.api.post("/api/market/" + listingId + "/messages", body)), sent -> {
            text.setText("");
            attachment = null;
            attachmentName.setText("No attachment selected");
            refresh();
        });
    }

    private void download() {
        Map<String, Object> message = messages.selected();
        if (message == null || Ui.s(message.get("downloadUrl")).isEmpty()) {
            Ui.info(this, "Select a message with an attachment first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(Ui.s(message.get("fileName"))));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        String path = Ui.s(message.get("downloadUrl"));
        Ui.run(this, () -> {
            Files.write(target, app.api.getBytes(path));
            return target;
        }, saved -> Ui.info(this, "Saved " + saved.getFileName() + "."));
    }
}
