package com.ewaste.client;

import java.util.List;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class NotificationsPage extends Page {
    private final MapTable table = new MapTable(col("", m -> Boolean.TRUE.equals(m.get("read")) ? "" : "NEW"),
            col("Date", m -> Ui.date(m.get("createdAt"))), key("Message", "message"));

    public NotificationsPage(App app) {
        super(app, "Notifications");
        add(top("Updates from the admin and the market", Ui.button("Refresh", this::refresh)), java.awt.BorderLayout.NORTH);
        add(table.scroll(), java.awt.BorderLayout.CENTER);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setMaxWidth(150);
    }

    @Override public void refresh() {
        Ui.run(this, () -> {
            List<Map<String, Object>> list = ApiClient.list(app.api.get("/api/notifications"));
            return list;
        }, list -> {
            table.setRows(list);
            boolean anyUnread = false;
            for (Map<String, Object> n : list) if (!Boolean.TRUE.equals(n.get("read"))) anyUnread = true;
            if (anyUnread) Ui.run(this, () -> app.api.post("/api/notifications/read", null), r -> app.refreshHeader.run());
        });
    }
}
