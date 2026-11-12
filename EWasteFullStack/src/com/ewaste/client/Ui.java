package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Small Swing helpers shared by all screens. */
public final class Ui {
    private Ui() {}

    public static final Color GREEN = new Color(0x2E7D32);
    public static final Color GREEN_DARK = new Color(0x1B5E20);
    public static final Color GREEN_LIGHT = new Color(0xE8F5E9);
    public static final Color GREY_TEXT = new Color(0x607D8B);

    public static void installLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.put("nimbusBase", new Color(0x2E7D32));
                    UIManager.put("nimbusBlueGrey", new Color(0x8FAF91));
                    UIManager.put("nimbusFocus", new Color(0x66BB6A));
                    UIManager.put("nimbusSelectionBackground", new Color(0x43A047));
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) { }
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        for (Object key : UIManager.getDefaults().keySet().toArray()) {
            Object v = UIManager.get(key);
            if (v instanceof javax.swing.plaf.FontUIResource) UIManager.put(key, new javax.swing.plaf.FontUIResource(f));
        }
    }

    // ---- background calls ---------------------------------------------------------------------
    /** Runs the job off the UI thread, then calls ok on the UI thread. Errors are shown in a dialog. */
    public static <T> void run(Component parent, Callable<T> job, Consumer<T> ok) {
        run(parent, job, ok, null);
    }

    public static <T> void run(Component parent, Callable<T> job, Consumer<T> ok, Runnable onFail) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return job.call(); }
            @Override protected void done() {
                try {
                    T r = get();
                    if (ok != null) ok.accept(r);
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    error(parent, t);
                    if (onFail != null) onFail.run();
                }
            }
        }.execute();
    }

    public static void error(Component parent, Throwable t) {
        String msg = t.getMessage() == null ? t.toString() : t.getMessage();
        JOptionPane.showMessageDialog(parent, msg, "Something went wrong", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "E-Waste Manager", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, "Please confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // ---- value formatting ---------------------------------------------------------------------
    public static String s(Object o) { return o == null ? "" : String.valueOf(o); }
    public static double d(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }
    public static int i(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }
    public static String money(Object o) { return String.format("$%.2f", d(o)); }
    public static String kg(Object o) { return String.format("%.1f kg", d(o)); }
    public static String date(Object iso) { String t = s(iso); return t.length() >= 16 ? t.substring(0, 10) + " " + t.substring(11, 16) : t; }

    /** Pretty multi-line description of an item map from the API. */
    public static String itemDetail(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        sb.append("Item #").append(m.get("id")).append(" - ").append(s(m.get("name")));
        if (!s(m.get("brand")).isEmpty()) sb.append(" (").append(s(m.get("brand"))).append(")");
        sb.append("\nOwner:        ").append(s(m.get("ownerName")));
        sb.append("\nCategory:     ").append(s(m.get("categoryLabel"))).append(Boolean.TRUE.equals(m.get("hazardous")) ? "  [HAZARDOUS]" : "");
        sb.append("\nAge / weight: ").append(m.get("ageYears")).append(" yrs / ").append(String.format("%.2f", d(m.get("weightKg")))).append(" kg");
        sb.append("\nCondition:    ").append(s(m.get("conditionLabel")));
        sb.append("\nStatus:       ").append(s(m.get("statusLabel")));
        sb.append("\nSuggested:    ").append(s(m.get("suggestedDecision")));
        sb.append("\nPlan:         ").append(m.get("decision") == null ? "-" : s(m.get("decision")));
        sb.append("\nEst. value:   ").append(money(m.get("estimatedValue")));
        if (d(m.get("repairCost")) > 0) sb.append("   (repair cost ").append(money(m.get("repairCost"))).append(")");
        if (m.get("pickupDate") != null) sb.append("\nPickup date:  ").append(s(m.get("pickupDate")));
        if (!s(m.get("adminNote")).isEmpty()) sb.append("\nAdmin note:   ").append(s(m.get("adminNote")));
        if (!s(m.get("description")).isEmpty()) sb.append("\nDescription:  ").append(s(m.get("description")));
        Object h = m.get("history");
        if (h instanceof List && !((List<?>) h).isEmpty()) {
            sb.append("\n\nTimeline:");
            for (Object line : (List<?>) h) sb.append("\n  * ").append(line);
        }
        Map<String, Object> report = ApiClient.obj(m.get("decisionReport"));
        if (report != null) sb.append("\n\n").append(decisionReport(report));
        return sb.toString();
    }

    public static String decisionReport(Map<String, Object> report) {
        if (report == null) return "Decision report is unavailable from this server version.";
        StringBuilder text = new StringBuilder();
        text.append("Recommended action: ").append(s(report.get("recommendedAction")));
        text.append("\nQuality: ").append(s(report.get("qualityIndicator")))
                .append(" (").append(report.get("inputCompletenessPercent")).append("% input completeness; not an ML probability)");
        text.append("\nRemaining useful life: ").append(report.get("remainingUsefulLifeYears")).append(" years");
        text.append("\nEstimated functional resale: ").append(money(report.get("estimatedResaleValue")));
        text.append("\nEstimated repair cost: ").append(money(report.get("repairCost")));
        text.append("\nRepair cost / functional resale: ")
                .append(String.format("%.1f%%", d(report.get("repairCostRatio")) * 100.0));
        text.append("\nEstimated recycling/material recovery: ").append(money(report.get("recyclingValue")));
        text.append("\nFinancial comparison (net estimates):");
        Map<String, Object> comparison = ApiClient.obj(report.get("financialComparison"));
        if (comparison != null) for (Map.Entry<String, Object> entry : comparison.entrySet())
            text.append("\n  ").append(entry.getKey()).append(": ").append(money(entry.getValue()));
        text.append("\nIndicative CO2 factor estimate: ").append(Ui.kg(report.get("environmentalImpactEstimateKgCo2")))
                .append("\n").append(s(report.get("environmentalImpactMethod")));
        appendReportLines(text, "Reasons", report.get("reasons"));
        appendReportLines(text, "Alternatives", report.get("alternativeActions"));
        appendReportLines(text, "Warnings", report.get("warnings"));
        return text.toString();
    }

    private static void appendReportLines(StringBuilder text, String heading, Object value) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) return;
        text.append("\n").append(heading).append(":");
        for (Object line : (List<?>) value) text.append("\n  - ").append(line);
    }

    public static void showDecisionReport(Component parent, String heading, Map<String, Object> report) {
        JTextArea area = readOnlyArea(18);
        area.setText(heading + "\n\n" + decisionReport(report));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(parent, new JScrollPane(area), "Explainable decision report", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- widgets --------------------------------------------------------------------------------
    /** Combo box entry showing a label but carrying the enum name used by the API. */
    public static final class Opt {
        public final String name, label;
        Opt(String name, String label) { this.name = name; this.label = label; }
        @Override public String toString() { return label; }
    }

    public static JComboBox<Opt> combo(List<Map<String, Object>> opts, String allLabel) {
        JComboBox<Opt> cb = new JComboBox<>();
        if (allLabel != null) cb.addItem(new Opt("", allLabel));
        for (Map<String, Object> o : opts) cb.addItem(new Opt(s(o.get("name")), s(o.get("label"))));
        return cb;
    }

    public static String sel(JComboBox<Opt> cb) { Opt o = (Opt) cb.getSelectedItem(); return o == null ? "" : o.name; }

    public static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    public static JButton primary(String text, Runnable action) {
        JButton b = button(text, action);
        b.setBackground(GREEN);
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }

    public static JTextArea readOnlyArea(int rows) {
        JTextArea a = new JTextArea(rows, 40);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setMargin(new Insets(6, 8, 6, 8));
        return a;
    }

    public static JPanel buttonRow(JComponent... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) p.add(c);
        return p;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 16f));
        l.setForeground(GREEN_DARK);
        return l;
    }

    /** A small "stat card": big value over a small caption. */
    public static JPanel card(String caption, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(GREEN_LIGHT);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xA5D6A7)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        value.setFont(value.getFont().deriveFont(Font.BOLD, 20f));
        value.setForeground(GREEN_DARK);
        JLabel c = new JLabel(caption);
        c.setForeground(GREY_TEXT);
        p.add(value, BorderLayout.CENTER);
        p.add(c, BorderLayout.SOUTH);
        return p;
    }

    /** Two-column label/field form. */
    public static final class Form extends JPanel {
        private int row;
        public Form() { super(new GridBagLayout()); }
        public Form add(String label, JComponent field) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row++;
            g.insets = new Insets(4, 4, 4, 8);
            g.anchor = GridBagConstraints.NORTHWEST;
            g.gridx = 0;
            super.add(new JLabel(label), g);
            g.gridx = 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            super.add(field, g);
            return this;
        }
    }
}
