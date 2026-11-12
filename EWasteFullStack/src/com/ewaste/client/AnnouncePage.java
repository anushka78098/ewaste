package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/** Announcements to all users, plus the status of email delivery with a "send test email" button. */
public class AnnouncePage extends Page {
    private final JTextArea message = new JTextArea(5, 40);
    private final JLabel mailState = new JLabel(" ");
    private final JLabel mailStats = new JLabel(" ");
    private final JButton test = Ui.button("Send test email...", this::sendTest);

    public AnnouncePage(App app) {
        super(app, "Announcements & email");
        message.setLineWrap(true);
        message.setWrapStyleWord(true);

        JPanel ann = new JPanel(new BorderLayout(6, 6));
        ann.setBorder(BorderFactory.createTitledBorder("Announcement to every active user"));
        ann.add(new JLabel("Shown in their Notifications tab and emailed to users who have email notifications on."), BorderLayout.NORTH);
        ann.add(new JScrollPane(message), BorderLayout.CENTER);
        ann.add(Ui.buttonRow(Ui.primary("Send to all users", this::send)), BorderLayout.SOUTH);

        JPanel mail = new JPanel(new GridLayout(3, 1, 0, 4));
        mail.setBorder(BorderFactory.createTitledBorder("Email delivery"));
        mail.add(mailState);
        mail.add(mailStats);
        mail.add(Ui.buttonRow(test, Ui.button("Refresh status", this::refresh)));

        JPanel col = new JPanel(new GridLayout(2, 1, 0, 12));
        col.add(ann);
        col.add(mail);
        col.setPreferredSize(new Dimension(640, 420));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrap.add(col);
        add(top("Announcements & email"), BorderLayout.NORTH);
        add(wrap, BorderLayout.CENTER);
    }

    private void send() {
        String text = message.getText().trim();
        if (text.isEmpty()) { Ui.info(this, "Write a message first."); return; }
        Ui.run(this, () -> ApiClient.obj(app.api.post("/api/admin/announce", ApiClient.map("message", text))), (Map<String, Object> r) -> {
            Ui.info(this, "Sent to " + r.get("sent") + " user(s).");
            message.setText("");
            refresh();
        });
    }

    private void sendTest() {
        String to = JOptionPane.showInputDialog(this, "Send a test email to:", Ui.s(app.user.get("email")));
        if (to == null || to.trim().isEmpty()) return;
        Ui.run(this, () -> app.api.post("/api/admin/mail/test", ApiClient.map("to", to.trim())),
                r -> { Ui.info(this, "Test email sent to " + to.trim() + ". Check the inbox (and spam folder)."); refresh(); }, this::refresh);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/admin/mail")), (Map<String, Object> m) -> {
            boolean on = Boolean.TRUE.equals(m.get("enabled"));
            test.setEnabled(on);
            if (!on) {
                mailState.setText("Email is OFF. Copy config/mail.properties.example to config/mail.properties, fill it in and restart the server.");
                mailStats.setText("Users still get every notification inside the app.");
            } else {
                mailState.setText("Email is ON: " + m.get("host") + ":" + m.get("port") + " (" + m.get("security") + "), from " + m.get("from"));
                String err = Ui.s(m.get("lastError"));
                mailStats.setText("Sent: " + m.get("sent") + "   Failed: " + m.get("failed") + "   Waiting: " + m.get("queued")
                        + (err.isEmpty() ? "" : "   Last error: " + err));
            }
        });
    }
}
