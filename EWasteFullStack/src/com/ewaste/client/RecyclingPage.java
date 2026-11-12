package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class RecyclingPage extends Page {
    private final JLabel eligible = new JLabel("-"), regular = new JLabel("-"), hazardous = new JLabel("-");
    private final MapTable batches = new MapTable(num("Batch", m -> m.get("id")), col("Date", m -> Ui.date(m.get("createdAt"))), key("Partner", "partner"),
            col("Hazardous", m -> Boolean.TRUE.equals(m.get("certified")) ? "certified" : "no"), num("Items", m -> m.get("itemCount")),
            num("Kg", m -> String.format("%.1f", Ui.d(m.get("totalKg")))), num("Metal", m -> String.format("%.1f", Ui.d(m.get("metalKg")))),
            num("Plastic", m -> String.format("%.1f", Ui.d(m.get("plasticKg")))), num("Glass", m -> String.format("%.1f", Ui.d(m.get("glassKg")))),
            num("CO2 saved", m -> String.format("%.1f kg", Ui.d(m.get("co2SavedKg")))));

    public RecyclingPage(App app) {
        super(app, "Recycling");
        JPanel cards = new JPanel(new GridLayout(1, 3, 10, 0));
        cards.add(Ui.card("collected, waiting to be queued", eligible));
        cards.add(Ui.card("queued - regular e-waste", regular));
        cards.add(Ui.card("queued - hazardous (batteries)", hazardous));
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(top("Recycling queue & shipments", Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        north.add(cards, BorderLayout.CENTER);
        north.add(Ui.buttonRow(Ui.button("Queue all recycle-planned items", this::queueAll),
                Ui.primary("Ship regular batch...", () -> ship(false)), Ui.primary("Ship hazardous batch...", () -> ship(true))), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        JPanel c = new JPanel(new BorderLayout(4, 4));
        c.add(Ui.heading("Batch history"), BorderLayout.NORTH);
        c.add(batches.scroll(), BorderLayout.CENTER);
        add(c, BorderLayout.CENTER);
        add(new JLabel("<html><i>Hazardous items can only be shipped to a certified hazardous-waste partner, so they never mix with regular e-waste.</i></html>"), BorderLayout.SOUTH);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/admin/recycling")), (Map<String, Object> m) -> {
            eligible.setText(Ui.s(m.get("eligible")));
            regular.setText(Ui.s(m.get("queuedRegular")));
            hazardous.setText(Ui.s(m.get("queuedHazardous")));
            batches.setRows(ApiClient.list(m.get("batches")));
        });
    }

    private void queueAll() {
        Ui.run(this, () -> ApiClient.obj(app.api.post("/api/admin/recycling/queue-all", null)),
                (Map<String, Object> r) -> { Ui.info(this, r.get("queued") + " item(s) queued."); refresh(); });
    }

    private void ship(boolean haz) {
        String partner = JOptionPane.showInputDialog(this, haz ? "Certified hazardous-waste partner name:" : "Recycling partner name:");
        if (partner == null) return;
        Ui.run(this, () -> ApiClient.obj(app.api.post("/api/admin/recycling/batches", ApiClient.map("partner", partner, "hazardous", haz))),
                (Map<String, Object> b) -> {
                    Ui.info(this, String.format("Batch #%s shipped to %s: %s items, %.1f kg.\nMetal %.1f kg, plastic %.1f kg, glass %.1f kg recovered.\nCO2 avoided: %.1f kg. Owners notified.",
                            b.get("id"), b.get("partner"), b.get("itemCount"), Ui.d(b.get("totalKg")), Ui.d(b.get("metalKg")),
                            Ui.d(b.get("plasticKg")), Ui.d(b.get("glassKg")), Ui.d(b.get("co2SavedKg"))));
                    refresh();
                });
    }
}
