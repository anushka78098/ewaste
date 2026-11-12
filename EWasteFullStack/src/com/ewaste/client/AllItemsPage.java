package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.ewaste.client.MapTable.*;

public class AllItemsPage extends Page {
    private final JTextField search = new JTextField(16);
    private final JComboBox<Ui.Opt> status;
    private final MapTable table = new MapTable(num("ID", m -> m.get("id")), key("Owner", "ownerName"), key("Item", "name"), key("Tracking", "trackingCode"), key("Category", "categoryLabel"),
            num("Age", m -> m.get("ageYears")), num("Kg", m -> String.format("%.1f", Ui.d(m.get("weightKg")))), key("Condition", "conditionLabel"),
            key("Status", "statusLabel"), key("Plan", "decision"));
    private final JTextArea detail = Ui.readOnlyArea(9);

    public AllItemsPage(App app) {
        super(app, "All items");
        status = Ui.combo(app.statuses(), "Any status");
        JPanel north = new JPanel(new BorderLayout());
        north.add(Ui.heading("Search all items"), BorderLayout.NORTH);
        north.add(Ui.buttonRow(new JLabel("Name / brand / owner:"), search, status, Ui.primary("Search", this::refresh),
                Ui.button("Export CSV...", this::export)), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, table.scroll(), new JScrollPane(detail));
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);
        search.addActionListener(e -> refresh());
        table.onSelect(m -> {
            if (m == null) return;
            Ui.run(this, () -> ApiClient.obj(app.api.get("/api/items/" + m.get("id"))), (Map<String, Object> full) -> {
                detail.setText(Ui.itemDetail(full));
                detail.setCaretPosition(0);
            });
        });
    }

    @Override public void refresh() {
        String q = "/api/admin/items?status=" + ApiClient.enc(Ui.sel(status)) + "&q=" + ApiClient.enc(search.getText().trim());
        Ui.run(this, () -> ApiClient.list(app.api.get(q)), table::setRows);
    }

    private void export() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("items.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        Ui.run(this, () -> {
            String csv = app.api.getText("/api/admin/export");
            java.nio.file.Files.write(f.toPath(), csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return f;
        }, saved -> Ui.info(this, "Saved " + saved.getName() + "."));
    }
}
