package com.ewaste.client.charts;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Vertical bars, e.g. items submitted per day. */
public class BarChart extends XYChart {
    public BarChart(String title) { super(title); }

    @Override void paintChart(Graphics2D g, Rectangle area) {
        double[] top = new double[1];
        Rectangle plot = paintAxes(g, area, top);
        int n = values.size();
        double slot = plot.width / (double) n;
        double bw = Math.max(2, Math.min(40, slot * 0.7));
        for (int i = 0; i < n; i++) {
            double v = values.get(i);
            double x = plot.x + i * slot + (slot - bw) / 2;
            double h = v / top[0] * plot.height;
            if (v > 0) {
                g.setColor(color);
                g.fill(new RoundRectangle2D.Double(x, plot.y + plot.height - h, bw, h, 4, 4));
                g.fillRect((int) x, (int) (plot.y + plot.height - Math.min(h, 4)), (int) bw, (int) Math.min(h, 4));   // flat bottom edge
            }
            hit(new Rectangle((int) (plot.x + i * slot), plot.y, (int) Math.ceil(slot), plot.height), labels.get(i) + ": " + format.apply(v));
        }
    }
}
