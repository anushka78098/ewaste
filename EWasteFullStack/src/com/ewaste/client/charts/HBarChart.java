package com.ewaste.client.charts;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** Horizontal bars with labels on the left: categories, funnels, rankings. */
public class HBarChart extends ChartBase {
    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private DoubleFunction<String> format = ChartBase::compact;
    private boolean varyColors;
    private Color color = Palette.series(0);

    public HBarChart(String title) { super(title); }

    public void setData(List<String> labels, List<Double> values) {
        this.labels = new ArrayList<>(labels);
        this.values = new ArrayList<>(values);
        repaint();
    }

    public void setFormat(DoubleFunction<String> f) { this.format = f; }

    public void setColor(Color c) { this.color = c; varyColors = false; }

    /** Each bar gets the next palette colour. */
    public void setVaryColors(boolean b) { varyColors = b; }

    @Override boolean hasData() {
        for (double v : values) if (v > 0) return true;
        return false;
    }

    @Override void paintChart(Graphics2D g, Rectangle area) {
        FontMetrics fm = g.getFontMetrics();
        int n = values.size();
        double max = 0;
        int labelW = 0;
        for (int i = 0; i < n; i++) { max = Math.max(max, values.get(i)); labelW = Math.max(labelW, fm.stringWidth(labels.get(i))); }
        labelW = Math.min(labelW, area.width / 3);
        int valueW = 0;
        for (double v : values) valueW = Math.max(valueW, fm.stringWidth(format.apply(v)));
        int barX = area.x + labelW + 8;
        int barMax = Math.max(10, area.width - labelW - valueW - 20);
        int rowH = Math.max(18, Math.min(34, area.height / Math.max(1, n)));
        int bh = Math.max(8, (int) (rowH * 0.58));
        for (int i = 0; i < n; i++) {
            int y = area.y + i * rowH;
            double v = values.get(i);
            g.setColor(Palette.AXIS_TEXT);
            g.drawString(clip(labels.get(i), fm, labelW), area.x, y + (rowH + fm.getAscent()) / 2 - 2);
            int w = max <= 0 ? 0 : (int) Math.round(v / max * barMax);
            g.setColor(varyColors ? Palette.series(i) : color);
            if (w > 0) g.fill(new RoundRectangle2D.Double(barX, y + (rowH - bh) / 2.0, w, bh, 6, 6));
            g.setColor(Palette.TITLE);
            g.drawString(format.apply(v), barX + w + 6, y + (rowH + fm.getAscent()) / 2 - 2);
            hit(new Rectangle(area.x, y, area.width, rowH), labels.get(i) + ": " + format.apply(v));
        }
    }
}
