package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class UsersPage extends Page {
    private final MapTable table = new MapTable(num("ID", m -> m.get("id")), key("Username", "username"), key("Name", "fullName"), key("Email", "email"),
            key("Phone", "phone"), num("Points", m -> m.get("points")), num("Items", m -> m.get("itemCount")),
            key("Role", "role"),
            col("Active", m -> Boolean.TRUE.equals(m.get("active")) ? "yes" : "NO"));

    public UsersPage(App app) {
        super(app, "Users");
        add(top("Registered users", Ui.button("Refresh", this::refresh), Ui.button("Activate / deactivate", this::toggle),
                Ui.button("Toggle recycler role", this::toggleRecycler), Ui.primary("Award bonus points...", this::award)), BorderLayout.NORTH);
        add(table.scroll(), BorderLayout.CENTER);
    }

    @Override public void refresh() { Ui.run(this, () -> ApiClient.list(app.api.get("/api/admin/users")), table::setRows); }

    private void toggle() {
        Map<String, Object> m = table.selected();
        if (m == null) { Ui.info(this, "Select a user first."); return; }
        boolean now = Boolean.TRUE.equals(m.get("active"));
        if (Ui.confirm(this, (now ? "Deactivate " : "Reactivate ") + m.get("username") + "?"))
            Ui.run(this, () -> app.api.post("/api/admin/users/" + m.get("id") + "/active", ApiClient.map("active", !now)), r -> refresh());
    }

    private void award() {
        Map<String, Object> m = table.selected();
        if (m == null) { Ui.info(this, "Select a user first."); return; }
        String p = JOptionPane.showInputDialog(this, "Bonus points for " + m.get("username") + " (1-10000):", "50");
        if (p == null) return;
        try {
            int v = Integer.parseInt(p.trim());
            Ui.run(this, () -> app.api.post("/api/admin/users/" + m.get("id") + "/points", ApiClient.map("points", v)), r -> refresh());
        } catch (NumberFormatException e) { Ui.info(this, "Enter a whole number."); }
    }

    private void toggleRecycler() {
        Map<String, Object> m = table.selected();
        if (m == null) { Ui.info(this, "Select a user first."); return; }
        String role = "RECYCLER".equals(m.get("role")) ? "USER" : "RECYCLER";
        String action = "RECYCLER".equals(role) ? "grant recycler access to " : "remove recycler access from ";
        if (Ui.confirm(this, "Do you want to " + action + m.get("username") + "?"))
            Ui.run(this, () -> app.api.post("/api/admin/users/" + m.get("id") + "/role", ApiClient.map("role", role)),
                    r -> refresh());
    }
}
