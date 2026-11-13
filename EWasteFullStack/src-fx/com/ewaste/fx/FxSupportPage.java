package com.ewaste.fx;

import com.ewaste.client.ApiClient;
import com.ewaste.client.App;
import com.ewaste.client.Ui;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/** Private support conversations for marketplace users and administrators. */
final class FxSupportPage extends FxPage {
    private final boolean admin;
    private final Fx.Grid inbox = new Fx.Grid().key("User", "fullName").key("Username", "username")
            .key("Latest message", "latestMessage").col("Unread", m -> m.get("unread"));
    private final TextArea conversation = Fx.readOnlyArea(18);
    private final TextArea draft = new TextArea();
    private final Label currentUser = Fx.muted("Select a user to view or start a conversation.");
    private Integer selectedUserId;
    private boolean updatingInbox;

    FxSupportPage(App app, boolean admin) {
        super(app, admin ? "Support inbox" : "Help & support");
        this.admin = admin;
        draft.setWrapText(true);
        draft.setPrefRowCount(3);
        inbox.onSelect(row -> {
            if (!updatingInbox && row != null) {
                selectedUserId = Ui.i(row.get("userId"));
                currentUser.setText(Ui.s(row.get("fullName")) + "  (" + Ui.s(row.get("username")) + ")");
                loadConversation();
            }
        });

        VBox detail = detail();
        if (admin) {
            VBox users = new VBox(8, Fx.heading("Users and conversations"), inbox);
            VBox.setVgrow(inbox, Priority.ALWAYS);
            SplitPane split = new SplitPane(users, detail);
            split.setDividerPositions(0.35);
            setCenter(split);
        } else {
            setCenter(detail);
        }
        setTop(header(admin ? "Private support conversations" : "Message administrator support",
                Fx.button("Refresh", this::refresh)));
    }

    private VBox detail() {
        conversation.setWrapText(true);
        VBox.setVgrow(conversation, Priority.ALWAYS);
        HBox composer = new HBox(8, draft, Fx.primary("Send", this::send));
        HBox.setHgrow(draft, Priority.ALWAYS);
        VBox body = new VBox(8, currentUser, conversation, composer);
        body.setPadding(new Insets(4, 0, 0, 0));
        return body;
    }

    @Override void refresh() {
        if (admin) refreshInbox(true);
        else loadConversation();
    }

    private void refreshInbox(boolean openSelected) {
        Fx.run(() -> ApiClient.list(app.api.get("/api/admin/support")), rows -> {
            updatingInbox = true;
            inbox.setRows(rows);
            updatingInbox = false;
            Map<String, Object> selected = inbox.selected();
            if (selected == null) {
                selectedUserId = null;
                currentUser.setText("Select a user to view or start a conversation.");
                conversation.clear();
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
        Fx.run(() -> ApiClient.list(app.api.get(path)), this::renderConversation);
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
        conversation.positionCaret(conversation.getLength());
        if (admin) refreshInbox(false);
    }

    private void send() {
        String text = draft.getText().trim();
        if (text.isEmpty()) return;
        if (admin && selectedUserId == null) {
            Fx.info("Select a user first.");
            return;
        }
        String path = admin ? "/api/admin/support/" + selectedUserId : "/api/me/support";
        Fx.run(() -> ApiClient.obj(app.api.post(path, ApiClient.map("text", text))), result -> {
            draft.clear();
            loadConversation();
        });
    }
}
