package com.ewaste.client.charts;

import java.awt.*;
import java.awt.geom.Ellipse2D;

/** A line with a light area fill, e.g. revenue over time. */
public class LineChart extends XYChart {
    public LineChart(String title) { super(title); }

    @Override void paintChart(Graphics2D g, Rectangle area) {
        double[] top = new double[1];
        Rectangle plot = paintAxes(g, area, top);
        int n = values.size();
        double slot = plot.width / (double) n;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) (plot.x + (i + 0.5) * slot);
            ys[i] = (int) (plot.y + plot.height - values.get(i) / top[0] * plot.height);
        }
        // area
        Polygon poly = new Polygon();
        poly.addPoint(xs[0], plot.y + plot.height);
        for (int i = 0; i < n; i++) poly.addPoint(xs[i], ys[i]);
        poly.addPoint(xs[n - 1], plot.y + plot.height);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
        g.fill(poly);
        // line
        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < n; i++) g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
        // points + hover columns
        for (int i = 0; i < n; i++) {
            if (n <= 45) {
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(xs[i] - 4, ys[i] - 4, 8, 8));
                g.setColor(color);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(xs[i] - 3, ys[i] - 3, 6, 6));
            }
            hit(new Rectangle((int) (plot.x + i * slot), plot.y, (int) Math.ceil(slot), plot.height), labels.get(i) + ": " + format.apply(values.get(i)));
        }
    }
}
