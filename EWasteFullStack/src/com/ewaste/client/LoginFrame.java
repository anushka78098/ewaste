package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class LoginFrame extends JFrame {
    private final JTextField server;
    private final JTextField user = new JTextField(18);
    private final JPasswordField pass = new JPasswordField(18);
    private final JLabel status = new JLabel(" ");

    public LoginFrame(String defaultServer) {
        super("E-Waste Manager - Sign in");
        server = new JTextField(defaultServer, 18);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel banner = new JPanel(new GridLayout(2, 1));
        banner.setBackground(Ui.GREEN);
        banner.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        JLabel t = new JLabel("E-Waste Manager");
        t.setForeground(Color.WHITE);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 24f));
        JLabel sub = new JLabel("Reuse. Repair. Recycle responsibly.");
        sub.setForeground(new Color(0xC8E6C9));
        banner.add(t);
        banner.add(sub);

        Ui.Form f = new Ui.Form();
        f.add("Server", server).add("Username", user).add("Password", pass);
        JButton login = Ui.primary("Sign in", this::login);
        JButton register = Ui.button("Create account...", this::register);
        JPanel buttons = Ui.buttonRow(login, register, Ui.button("Track an item...", this::track));
        status.setForeground(Ui.GREY_TEXT);

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        body.add(f, BorderLayout.NORTH);
        body.add(buttons, BorderLayout.CENTER);
        body.add(status, BorderLayout.SOUTH);

        add(banner, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        getRootPane().setDefaultButton(login);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        checkServer();
    }

    private void checkServer() {
        ApiClient c = new ApiClient(server.getText());
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                try { return "Server online (" + ApiClient.obj(c.get("/api/health")).get("store") + ")"; }
                catch (RuntimeException e) { return "Server not reachable - start it first (see README)."; }
            }
            @Override protected void done() { try { status.setText(get()); } catch (Exception ignored) { } }
        }.execute();
    }

    private void login() {
        ApiClient api = new ApiClient(server.getText());
        App app = new App(api);
        String u = user.getText().trim(), p = new String(pass.getPassword());
        status.setText("Signing in...");
        Ui.run(this, () -> {
            app.user = api.login(u, p);
            app.loadMeta();
            return app;
        }, a -> {
            pass.setText("");
            dispose();
            Runnable back = () -> new LoginFrame(api.baseUrl()).setVisible(true);
            (a.isAdmin() ? AdminFrame.create(a, back) : a.isRecycler()
                    ? RecyclerFrame.create(a, back) : UserFrame.create(a, back)).setVisible(true);
        }, () -> status.setText("Sign-in failed."));
    }

    private void register() {
        JTextField u = new JTextField(18), name = new JTextField(18), email = new JTextField(18), phone = new JTextField(18), addr = new JTextField(18);
        JPasswordField pw = new JPasswordField(18);
        Ui.Form f = new Ui.Form();
        f.add("Username *", u).add("Password * (min 6)", pw).add("Full name *", name).add("Email *", email).add("Phone", phone).add("Address", addr);
        if (JOptionPane.showConfirmDialog(this, f, "Create account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        ApiClient api = new ApiClient(server.getText());
        Map<String, Object> body = ApiClient.map("username", u.getText().trim(), "password", new String(pw.getPassword()),
                "fullName", name.getText().trim(), "email", email.getText().trim(), "phone", phone.getText().trim(), "address", addr.getText().trim());
        Ui.run(this, () -> api.post("/api/auth/register", body), r -> {
            user.setText(u.getText().trim());
            pass.setText("");
            status.setText("Account created - sign in below.");
        });
    }

    /** Public tracker: anyone with a code (EW-yyyy-nnnnn) can see progress, no account needed. */
    private void track() {
        String code = JOptionPane.showInputDialog(this, "Tracking code (looks like EW-2026-00012):", "Track an item", JOptionPane.QUESTION_MESSAGE);
        if (code == null || code.trim().isEmpty()) return;
        ApiClient api = new ApiClient(server.getText());
        Ui.run(this, () -> ApiClient.obj(api.get("/api/track/" + ApiClient.enc(code.trim()))), r -> {
            StringBuilder sb = new StringBuilder();
            sb.append(Ui.s(r.get("trackingCode"))).append("  -  ").append(Ui.s(r.get("categoryLabel"))).append("\n");
            sb.append("Status: ").append(Ui.s(r.get("statusLabel"))).append("\n");
            if (r.get("pickupDate") != null) sb.append("Pickup date: ").append(Ui.s(r.get("pickupDate"))).append("\n");
            sb.append("\n");
            for (Object o : (java.util.List<?>) r.get("steps")) {
                Map<String, Object> st = ApiClient.obj(o);
                sb.append(Boolean.TRUE.equals(st.get("done")) ? "[x] " : "[ ] ").append(Ui.s(st.get("label"))).append("\n");
            }
            JTextArea a = Ui.readOnlyArea(Math.min(14, 6 + ((java.util.List<?>) r.get("steps")).size()));
            a.setText(sb.toString());
            JOptionPane.showMessageDialog(this, new JScrollPane(a), "Item progress", JOptionPane.PLAIN_MESSAGE);
        });
    }
}
