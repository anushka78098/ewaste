package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/** Private support conversations for marketplace users and administrators. */
public final class SupportMessagesPage extends Page {
    private final boolean admin;
    private final MapTable inbox = new MapTable(
            MapTable.key("User", "fullName"),
            MapTable.key("Username", "username"),
            MapTable.key("Latest message", "latestMessage"),
            MapTable.num("Unread", m -> m.get("unread")));
    private final JTextArea conversation = Ui.readOnlyArea(18);
    private final JTextArea draft = new JTextArea(3, 60);
    private final JLabel currentUser = new JLabel("Select a user to view or start a conversation.");
    private Integer selectedUserId;
    private boolean updatingInbox;

    public SupportMessagesPage(App app, boolean admin) {
        super(app, admin ? "Support inbox" : "Help & support");
        this.admin = admin;
        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);
        inbox.onSelect(row -> {
            if (!updatingInbox && row != null) {
                selectedUserId = Ui.i(row.get("userId"));
                currentUser.setText(Ui.s(row.get("fullName")) + "  (" + Ui.s(row.get("username")) + ")");
                loadConversation();
            }
        });

        JPanel transcript = new JPanel(new BorderLayout(8, 8));
        transcript.add(currentUser, BorderLayout.NORTH);
        transcript.add(new JScrollPane(conversation), BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.add(new JScrollPane(draft), BorderLayout.CENTER);
        composer.add(Ui.buttonRow(Ui.primary("Send", this::send)), BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        if (admin) {
            JPanel users = new JPanel(new BorderLayout(8, 8));
            users.add(Ui.heading("Users and conversations"), BorderLayout.NORTH);
            users.add(inbox.scroll(), BorderLayout.CENTER);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, users, transcript);
            split.setResizeWeight(0.35);
            body.add(split, BorderLayout.CENTER);
        } else {
            body.add(transcript, BorderLayout.CENTER);
        }
        body.add(composer, BorderLayout.SOUTH);

        add(top(admin ? "Private support conversations" : "Message administrator support",
                Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        if (admin) refreshInbox(true);
        else loadConversation();
    }

    private void refreshInbox(boolean openSelected) {
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/admin/support")), rows -> {
            updatingInbox = true;
            inbox.setRows(rows);
            updatingInbox = false;
            Map<String, Object> selected = inbox.selected();
            if (selected == null) {
                selectedUserId = null;
                currentUser.setText("Select a user to view or start a conversation.");
                conversation.setText("");
            } else {
                selectedUserId = Ui.i(selected.get("userId"));
                currentUser.setText(Ui.s(selected.get("fullName")) + "  (" + Ui.s(selected.get("username")) + ")");
                if (openSelected) loadConversation();
            }
        });
    }

    private void loadConversation() {
        if (admin && selectedUserId == null) return;
        String path = admin ? "/api/admin/support/" + selectedUserId : "/api/me/support";
        Ui.run(this, () -> ApiClient.list(app.api.get(path)), this::renderConversation);
    }

    private void renderConversation(List<Map<String, Object>> messages) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> message : messages) {
            boolean fromAdmin = Boolean.TRUE.equals(message.get("fromAdmin"));
            String sender = admin ? (fromAdmin ? "You" : Ui.s(message.get("senderName")))
                    : (fromAdmin ? "Support" : "You");
            text.append('[').append(Ui.date(message.get("createdAt"))).append("] ")
                    .append(sender).append(":\n").append(Ui.s(message.get("text"))).append("\n\n");
        }
        conversation.setText(text.length() == 0 ? "No messages yet. Send a message to start the conversation." : text.toString());
        conversation.setCaretPosition(conversation.getDocument().getLength());
        if (admin) refreshInbox(false);
    }

    private void send() {
        String text = draft.getText().trim();
        if (text.isEmpty()) return;
        if (admin && selectedUserId == null) {
            Ui.info(this, "Select a user first.");
            return;
        }
        String path = admin ? "/api/admin/support/" + selectedUserId : "/api/me/support";
        Ui.run(this, () -> ApiClient.obj(app.api.post(path, ApiClient.map("text", text))), result -> {
            draft.setText("");
            loadConversation();
        });
    }
}
