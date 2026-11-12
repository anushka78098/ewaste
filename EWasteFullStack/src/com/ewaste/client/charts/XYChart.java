package com.ewaste.client.charts;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/** Shared axes for bar and line charts: one numeric series over labelled categories (e.g. days). */
abstract class XYChart extends ChartBase {
    protected List<String> labels = new ArrayList<>();
    protected List<Double> values = new ArrayList<>();
    protected Color color = Palette.series(0);
    protected DoubleFunction<String> format = ChartBase::compact;

    XYChart(String title) { super(title); }

    public void setData(List<String> labels, List<Double> values) {
        this.labels = new ArrayList<>(labels);
        this.values = new ArrayList<>(values);
        repaint();
    }

    public void setColor(Color c) { this.color = c; }

    public void setFormat(DoubleFunction<String> f) { this.format = f; }

    @Override boolean hasData() {
        for (double v : values) if (v > 0) return true;
        return false;
    }

    /** Draws gridlines and labels; returns the plot rectangle. top[0] receives the y-axis maximum. */
    protected Rectangle paintAxes(Graphics2D g, Rectangle area, double[] top) {
        double max = 0;
        for (double v : values) max = Math.max(max, v);
        double yMax = niceMax(max);
        top[0] = yMax;
        int ticks = 5;
        boolean whole = true;
        for (double v : values) if (v != Math.rint(v)) whole = false;
        if (whole && yMax < 5) ticks = (int) yMax;   // counts: 0,1,2 instead of 0,0.4,0.8 ...
        FontMetrics fm = g.getFontMetrics();
        int left = 0;
        for (int i = 0; i <= ticks; i++) left = Math.max(left, fm.stringWidth(compact(yMax * i / ticks)));
        left += 8;
        int bottom = fm.getHeight() + 4;
        Rectangle plot = new Rectangle(area.x + left, area.y + 8, area.width - left - 4, area.height - bottom - 8);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= ticks; i++) {
            int y = plot.y + plot.height - i * plot.height / ticks;
            g.setColor(Palette.GRID);
            g.drawLine(plot.x, y, plot.x + plot.width, y);
            g.setColor(Palette.AXIS_TEXT);
            String t = compact(yMax * i / ticks);
            g.drawString(t, plot.x - 6 - fm.stringWidth(t), y + fm.getAscent() / 2 - 1);
        }
        int n = labels.size();
        if (n > 0) {
            int widest = 0;
            for (String l : labels) widest = Math.max(widest, fm.stringWidth(l));
            int step = Math.max(1, (int) Math.ceil(n * (widest + 10.0) / plot.width));
            double slot = plot.width / (double) n;
            g.setColor(Palette.AXIS_TEXT);
            for (int i = 0; i < n; i += step) {
                int cx = (int) (plot.x + (i + 0.5) * slot);
                g.drawString(labels.get(i), cx - fm.stringWidth(labels.get(i)) / 2, plot.y + plot.height + fm.getAscent() + 3);
            }
        }
        return plot;
    }
}
