package com.ewaste.client.charts;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Common frame for the charts: white card, title, empty-state message, hover tooltips. */
abstract class ChartBase extends JPanel {
    protected final String title;
    protected String emptyText = "No data for this period";
    private final List<Shape> hitShapes = new ArrayList<>();
    private final List<String> hitTips = new ArrayList<>();

    ChartBase(String title) {
        this.title = title;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Palette.BORDER));
        setPreferredSize(new Dimension(380, 250));
        setMinimumSize(new Dimension(240, 190));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    abstract boolean hasData();

    abstract void paintChart(Graphics2D g, Rectangle area);

    protected void hit(Shape s, String tip) {
        hitShapes.add(s);
        hitTips.add(tip);
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hitShapes.clear();
        hitTips.clear();
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(Palette.TITLE);
        g.drawString(title, 12, 20);
        Rectangle area = new Rectangle(12, 30, Math.max(10, getWidth() - 24), Math.max(10, getHeight() - 42));
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        if (!hasData()) {
            g.setColor(Palette.AXIS_TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(emptyText, area.x + (area.width - fm.stringWidth(emptyText)) / 2, area.y + area.height / 2);
        } else {
            paintChart(g, area);
        }
        g.dispose();
    }

    @Override public String getToolTipText(MouseEvent e) {
        for (int i = hitShapes.size() - 1; i >= 0; i--) if (hitShapes.get(i).contains(e.getPoint())) return hitTips.get(i);
        return null;
    }

    // ---- helpers --------------------------------------------------------------------------
    /** Rounds up to 1, 2, 5 or 10 x 10^k so that five equal ticks have clean values. */
    static double niceMax(double max) {
        if (max <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(max)));
        double f = max / exp;
        double nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return nf * exp;
    }

    static String compact(double v) {
        if (Math.abs(v) >= 1000) return String.format("%.1fk", v / 1000);
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }

    static String clip(String s, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(s) <= maxWidth) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}
