package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ProfilePage extends Page {
    private final JTextField fullName = new JTextField(24), email = new JTextField(24), phone = new JTextField(24), address = new JTextField(24);
    private final JCheckBox emailMe = new JCheckBox("Email me my notifications (in-app notifications are always kept)", true);
    private final JPasswordField oldPw = new JPasswordField(24), newPw = new JPasswordField(24), confirmPw = new JPasswordField(24);

    public ProfilePage(App app) {
        super(app, "Profile");
        Ui.Form contact = new Ui.Form();
        contact.add("Full name", fullName).add("Email", email).add("Phone", phone).add("Address", address).add("", emailMe);
        JPanel c = new JPanel(new BorderLayout());
        c.setBorder(BorderFactory.createTitledBorder("Contact details"));
        c.add(contact, BorderLayout.CENTER);
        c.add(Ui.buttonRow(Ui.primary("Save details", this::saveProfile)), BorderLayout.SOUTH);

        Ui.Form pw = new Ui.Form();
        pw.add("Current password", oldPw).add("New password", newPw).add("Confirm new", confirmPw);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Change password"));
        p.add(pw, BorderLayout.CENTER);
        p.add(Ui.buttonRow(Ui.primary("Change password", this::changePassword)), BorderLayout.SOUTH);

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(c);
        col.add(Box.createVerticalStrut(12));
        col.add(p);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrap.add(col);
        emailMe.setVisible(!app.isAdmin());
        add(top("My profile"), BorderLayout.NORTH);
        add(wrap, BorderLayout.CENTER);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/me")), (Map<String, Object> m) -> {
            fullName.setText(Ui.s(m.get("fullName")));
            email.setText(Ui.s(m.get("email")));
            phone.setText(Ui.s(m.get("phone")));
            address.setText(Ui.s(m.get("address")));
            emailMe.setSelected(!Boolean.TRUE.equals(m.get("emailOptOut")));
        });
    }

    private void saveProfile() {
        Map<String, Object> body = ApiClient.map("fullName", fullName.getText().trim(), "email", email.getText().trim(),
                "phone", phone.getText().trim(), "address", address.getText().trim(), "emailOptOut", !emailMe.isSelected());
        Ui.run(this, () -> app.api.put("/api/me", body), r -> { Ui.info(this, "Profile updated."); app.refreshHeader.run(); });
    }

    private void changePassword() {
        String n = new String(newPw.getPassword());
        if (!n.equals(new String(confirmPw.getPassword()))) { Ui.info(this, "The new passwords do not match."); return; }
        Map<String, Object> body = ApiClient.map("oldPassword", new String(oldPw.getPassword()), "newPassword", n);
        Ui.run(this, () -> app.api.post("/api/me/password", body), r -> {
            Ui.info(this, "Password changed.");
            oldPw.setText(""); newPw.setText(""); confirmPw.setText("");
        });
    }
}
