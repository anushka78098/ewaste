package com.ewaste.client;

import javax.swing.*;
import java.awt.*;

/** Big number with a caption and, optionally, a change against the previous period. */
public class KpiCard extends JPanel {
    private static final Color[] START = {
            new Color(0x4267D5), new Color(0x8150D8), new Color(0xA86F00),
            new Color(0x0B807F), new Color(0x1474A6)
    };
    private static final Color[] END = {
            new Color(0x6688F0), new Color(0xAA70E8), new Color(0xCC941B),
            new Color(0x20A5A1), new Color(0x2D9BC8)
    };
    private final JLabel value = new JLabel("-"), delta = new JLabel(" ");
    private final Color start, end;

    public KpiCard(String caption) {
        this(caption, 0);
    }

    public KpiCard(String caption, int colorIndex) {
        super(new BorderLayout(0, 2));
        start = START[Math.floorMod(colorIndex, START.length)];
        end = END[Math.floorMod(colorIndex, END.length)];
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        value.setFont(value.getFont().deriveFont(Font.BOLD, 22f));
        value.setForeground(Color.WHITE);
        JLabel c = new JLabel(caption);
        c.setForeground(new Color(0xF1F5FF));
        delta.setFont(delta.getFont().deriveFont(Font.PLAIN, 12f));
        delta.setForeground(new Color(0xE5EDFF));
        JPanel south = new JPanel(new GridLayout(2, 1));
        south.setOpaque(false);
        south.add(c);
        south.add(delta);
        add(value, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        g2.dispose();
    }

    public void set(String text) { value.setText(text); delta.setText(" "); }

    /** Shows the change vs the previous period, e.g. "+25% vs previous 30 days". */
    public void set(String text, double current, double previous, int days) {
        value.setText(text);
        if (previous <= 0 && current <= 0) { delta.setText("no activity"); return; }
        if (previous <= 0) { delta.setText("new vs previous " + days + " days"); return; }
        double pct = (current - previous) / previous * 100.0;
        String arrow = pct > 0.5 ? "▲ " : pct < -0.5 ? "▼ " : "= ";
        delta.setText(String.format("%s%+.0f%% vs previous %d days", arrow, pct, days));
    }
}
