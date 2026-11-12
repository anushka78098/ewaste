package com.ewaste.client.charts;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/** Ring chart with a legend; the centre shows the total. */
public class DonutChart extends ChartBase {
    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private String centerCaption = "items";

    public DonutChart(String title) { super(title); }

    public void setData(List<String> labels, List<Double> values) {
        this.labels = new ArrayList<>(labels);
        this.values = new ArrayList<>(values);
        repaint();
    }

    public void setCenterCaption(String c) { centerCaption = c; }

    @Override boolean hasData() {
        for (double v : values) if (v > 0) return true;
        return false;
    }

    @Override void paintChart(Graphics2D g, Rectangle area) {
        double total = 0;
        for (double v : values) total += v;
        int size = Math.min(area.height, (int) (area.width * 0.52));
        int ox = area.x + 4, oy = area.y + (area.height - size) / 2;
        double start = 90;
        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            if (v <= 0) continue;
            double extent = -v / total * 360.0;
            Arc2D.Double arc = new Arc2D.Double(ox, oy, size, size, start, extent, Arc2D.PIE);
            g.setColor(Palette.series(i));
            g.fill(arc);
            hit(arc, labels.get(i) + ": " + compact(v) + String.format(" (%.0f%%)", v / total * 100));
            start += extent;
        }
        int hole = (int) (size * 0.58);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(ox + (size - hole) / 2.0, oy + (size - hole) / 2.0, hole, hole));
        FontMetrics fm = g.getFontMetrics();
        Font old = g.getFont();
        g.setFont(old.deriveFont(Font.BOLD, 18f));
        String t = compact(total);
        FontMetrics big = g.getFontMetrics();
        g.setColor(Palette.TITLE);
        g.drawString(t, ox + (size - big.stringWidth(t)) / 2, oy + size / 2 + 2);
        g.setFont(old);
        g.setColor(Palette.AXIS_TEXT);
        g.drawString(centerCaption, ox + (size - fm.stringWidth(centerCaption)) / 2, oy + size / 2 + 16);

        // legend
        int lx = ox + size + 18, rowH = 20, shown = 0;
        for (int i = 0; i < values.size(); i++) if (values.get(i) > 0) shown++;
        int ly = area.y + Math.max(0, (area.height - shown * rowH) / 2);
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) <= 0) continue;
            g.setColor(Palette.series(i));
            g.fillRoundRect(lx, ly + 3, 10, 10, 3, 3);
            g.setColor(Palette.AXIS_TEXT);
            String line = labels.get(i) + "  " + compact(values.get(i));
            g.drawString(clip(line, fm, area.x + area.width - lx - 18), lx + 16, ly + 12);
            ly += rowH;
        }
    }
}
