package com.ewaste.client.charts;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** A slim progress bar with a caption inside, used for reward tiers and collection drives. */
public class Meter extends JComponent {
    private double fraction;
    private String text = "";
    private Color color = Palette.series(0);

    public Meter() { setPreferredSize(new Dimension(260, 20)); setMinimumSize(new Dimension(80, 20)); }

    public void set(double fraction, String text) {
        this.fraction = Math.max(0, Math.min(1, fraction));
        this.text = text == null ? "" : text;
        repaint();
    }

    public void setColor(Color c) { color = c; }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(new Color(0xE3E8EB));
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
        if (fraction > 0) {
            g.setColor(color);
            g.setClip(new RoundRectangle2D.Double(0, 0, w, h, h, h));
            g.fillRect(0, 0, (int) Math.max(h, w * fraction), h);
            g.setClip(null);
        }
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g.getFontMetrics();
        int tx = Math.max(8, (w - fm.stringWidth(text)) / 2), ty = (h + fm.getAscent()) / 2 - 2;
        g.setColor(fraction > 0.5 ? Color.WHITE : Palette.TITLE);
        g.drawString(text, tx, ty);
        g.dispose();
    }
}
