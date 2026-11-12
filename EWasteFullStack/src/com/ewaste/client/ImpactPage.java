package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class ImpactPage extends Page {
    private final JLabel points = new JLabel("-"), kg = new JLabel("-"), co2 = new JLabel("-");
    private final MapTable board = new MapTable(num("Rank", m -> m.get("rank")), key("User", "fullName"),
            num("kg donated", m -> String.format("%.1f", Ui.d(m.get("kgDonated")))), num("Points", m -> m.get("points")));
    private final MapTable guide;

    public ImpactPage(App app) {
        super(app, "Rewards & impact");
        guide = new MapTable(key("Category", "label"), num("Max reuse age", m -> m.get("maxReuseAge") + " yrs"),
                num("Points per kg", m -> m.get("pointsPerKg")), col("Hazardous", m -> Boolean.TRUE.equals(m.get("hazardous")) ? "yes" : "no"));
        guide.setRows(app.categories());

        JPanel cards = new JPanel(new GridLayout(1, 3, 10, 0));
        cards.add(Ui.card("reward points (redeemable in the market)", points));
        cards.add(Ui.card("of e-waste handed in", kg));
        cards.add(Ui.card("CO2 emissions avoided", co2));

        JPanel lower = new JPanel(new GridLayout(1, 2, 12, 0));
        JPanel l = new JPanel(new BorderLayout(4, 4));
        l.add(Ui.heading("Top contributors"), BorderLayout.NORTH);
        l.add(board.scroll(), BorderLayout.CENTER);
        JPanel r = new JPanel(new BorderLayout(4, 4));
        r.add(Ui.heading("What earns points"), BorderLayout.NORTH);
        r.add(guide.scroll(), BorderLayout.CENTER);
        lower.add(l);
        lower.add(r);

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(top("Your impact", Ui.button("Refresh", this::refresh)), BorderLayout.NORTH);
        north.add(cards, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(lower, BorderLayout.CENTER);
        add(new JLabel("<html><i>Points are earned when an item is collected (+50% for reusable items) and when a donated item is sold.</i></html>"), BorderLayout.SOUTH);
    }

    @Override public void refresh() {
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/me/impact")), (Map<String, Object> m) -> {
            points.setText(Ui.i(m.get("points")) + "  (" + Ui.money(m.get("pointsValue")) + ")");
            kg.setText(Ui.kg(m.get("kgDonated")));
            co2.setText(Ui.kg(m.get("co2Saved")));
        });
        Ui.run(this, () -> ApiClient.list(app.api.get("/api/leaderboard")), board::setRows);
    }
}
