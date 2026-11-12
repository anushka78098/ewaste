package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import javax.imageio.ImageIO;

import static com.ewaste.client.MapTable.*;

public class MarketPage extends Page {
    private final JTextField keyword = new JTextField(14), maxPrice = new JTextField(6);
    private final JComboBox<Ui.Opt> category;
    private final MapTable table = new MapTable(num("Listing", m -> m.get("id")), col("Item", m -> Ui.s(m.get("itemName"))
            + (Ui.s(m.get("brand")).isEmpty() ? "" : " - " + m.get("brand"))), key("Category", "categoryLabel"),
            key("Condition", "conditionLabel"), num("Age", m -> m.get("ageYears") + " y"),
            num("Circularity", m -> m.get("circularityScore") + "/100"), num("Price", m -> Ui.money(m.get("price"))));
    private final JTextArea detail = Ui.readOnlyArea(4);
    private final JLabel photo = new JLabel("No listing photo", SwingConstants.CENTER);

    public MarketPage(App app) {
        super(app, "Market");
        category = Ui.combo(app.categories(), "All categories");
        JPanel bar = Ui.buttonRow(new JLabel("Search:"), keyword, category, new JLabel("Max price:"), maxPrice,
                Ui.button("Search", this::refresh), Ui.button("Message seller...", this::messageSeller),
                Ui.button("Circularity passport...", this::showPassport),
                Ui.primary("Buy selected...", this::buy));
        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Reused & refurbished devices"), BorderLayout.NORTH);
        north.add(bar, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(table.scroll(), BorderLayout.CENTER);
        photo.setPreferredSize(new Dimension(200, 150));
        JPanel details = new JPanel(new BorderLayout(8, 0));
        details.add(new JScrollPane(detail), BorderLayout.CENTER);
        details.add(photo, BorderLayout.EAST);
        add(details, BorderLayout.SOUTH);
        keyword.addActionListener(e -> refresh());
        maxPrice.addActionListener(e -> refresh());
        table.onSelect(m -> {
            detail.setText(m == null ? "" : Ui.s(m.get("itemName")) + "\nSeller: " + Ui.s(m.get("sellerName"))
                    + "\n" + (Ui.s(m.get("description")).isEmpty() ? "No description." : m.get("description")));
            photo.setIcon(null);
            photo.setText("No listing photo");
            if (m != null && !Ui.s(m.get("photoUrl")).isEmpty()) {
                String url = Ui.s(m.get("photoUrl"));
                Ui.run(this, () -> ImageIO.read(new ByteArrayInputStream(app.api.getBytes(url))), this::showPhoto);
            }
        });
    }

    private void showPhoto(BufferedImage image) {
        if (image == null) { photo.setText("Photo unavailable"); return; }
        Image scaled = image.getScaledInstance(190, 140, Image.SCALE_SMOOTH);
        photo.setText("");
        photo.setIcon(new ImageIcon(scaled));
    }

    @Override public void refresh() {
        String q = "/api/market?q=" + ApiClient.enc(keyword.getText().trim()) + "&category=" + ApiClient.enc(Ui.sel(category))
                + "&maxPrice=" + ApiClient.enc(maxPrice.getText().trim());
        Ui.run(this, () -> ApiClient.list(app.api.get(q)), table::setRows);
    }

    private void buy() {
        Map<String, Object> l = table.selected();
        if (l == null) { Ui.info(this, "Select an item first."); return; }
        double price = Ui.d(l.get("price"));
        int have = Ui.i(app.user.get("points"));
        int max = Math.max(0, Math.min(have, (int) Math.floor(price * app.maxPointDiscount() / app.pointValue())));
        JSpinner pts = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(max, 0), 10));
        JLabel total = new JLabel();
        Runnable upd = () -> total.setText("You pay: " + Ui.money(price - ((Number) pts.getValue()).intValue() * app.pointValue()) + "  (pay on delivery)");
        pts.addChangeListener(e -> upd.run());
        upd.run();
        Ui.Form f = new Ui.Form();
        f.add("Item", new JLabel(Ui.s(l.get("itemName")))).add("Price", new JLabel(Ui.money(price)))
         .add("Your points", new JLabel(have + "  (1 point = " + Ui.money(app.pointValue()) + ", max " + (int) (app.maxPointDiscount() * 100) + "% off)"))
         .add("Redeem points", pts).add("", total);
        if (JOptionPane.showConfirmDialog(this, f, "Buy " + l.get("itemName"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        int use = ((Number) pts.getValue()).intValue();
        Ui.run(this, () -> ApiClient.obj(app.api.post("/api/market/" + l.get("id") + "/buy", ApiClient.map("points", use))), (Map<String, Object> o) -> {
            Ui.info(this, "Order #" + o.get("id") + " placed. You pay " + Ui.money(o.get("amountPaid")) + " on delivery.");
            app.reloadUserQuiet();
            app.refreshHeader.run();
            refresh();
        }, this::refresh);
    }

    private void messageSeller() {
        Map<String, Object> l = table.selected();
        if (l == null) { Ui.info(this, "Select a listing first."); return; }
        int ownerId = Ui.i(l.get("ownerId"));
        if (ownerId == Ui.i(app.user.get("id"))) { Ui.info(this, "This is your own listing."); return; }
        app.openConversation.accept(Ui.i(l.get("id")), ownerId);
    }

    private void showPassport() {
        Map<String, Object> l = table.selected();
        if (l == null) { Ui.info(this, "Select a listing first."); return; }
        Ui.run(this, () -> ApiClient.obj(app.api.get("/api/passport/" + Ui.s(l.get("trackingCode")))), passport -> {
            StringBuilder report = new StringBuilder();
            report.append(Ui.s(passport.get("itemName"))).append(" — ").append(Ui.s(passport.get("categoryLabel"))).append('\n')
                    .append("Circularity score: ").append(passport.get("circularityScore")).append("/100 — ")
                    .append(passport.get("scoreLabel")).append("\nTracking code: ").append(passport.get("trackingCode"))
                    .append("\nCondition: ").append(passport.get("conditionLabel")).append(" | Age: ").append(passport.get("ageYears"))
                    .append(" years | Weight: ").append(Ui.kg(passport.get("weightKg")))
                    .append("\n\nEstimated CO2 avoided: ").append(Ui.kg(passport.get("co2AvoidedKg")))
                    .append("\nPotential if reused: ").append(Ui.kg(passport.get("co2PotentialKg")))
                    .append("\n\nSCORE BREAKDOWN\n");
            for (Map<String, Object> f : ApiClient.list(passport.get("scoreFactors")))
                report.append("• ").append(f.get("name")).append(": ").append(f.get("score")).append('/').append(f.get("max"))
                        .append(" — ").append(f.get("explanation")).append('\n');
            Map<String, Object> materials = ApiClient.obj(passport.get("materialsKg"));
            report.append("\nESTIMATED MATERIAL CONTENT\nMetal ").append(Ui.kg(materials.get("metalKg")))
                    .append(" | Plastic ").append(Ui.kg(materials.get("plasticKg")))
                    .append(" | Glass ").append(Ui.kg(materials.get("glassKg")))
                    .append(" | Hazardous ").append(Ui.kg(materials.get("hazardousKg")))
                    .append(" | Other ").append(Ui.kg(materials.get("otherKg"))).append("\n\nJOURNEY\n");
            for (Map<String, Object> step : ApiClient.list(passport.get("journey")))
                report.append(Boolean.TRUE.equals(step.get("complete")) ? "[x] " : "[ ] ").append(step.get("label")).append('\n');
            report.append("\n").append(passport.get("impactMethod")).append("\n").append(passport.get("privacyNote"));
            JTextArea content = Ui.readOnlyArea(22);
            content.setText(report.toString());
            content.setCaretPosition(0);
            JOptionPane.showMessageDialog(this, new JScrollPane(content), "Circularity Passport", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
