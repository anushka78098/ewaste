package com.ewaste.client;

import com.ewaste.client.charts.Palette;

import javax.swing.*;
import java.awt.*;

/** Big number with a caption and, optionally, a change against the previous period. */
public class KpiCard extends JPanel {
    private final JLabel value = new JLabel("-"), delta = new JLabel(" ");

    public KpiCard(String caption) {
        super(new BorderLayout(0, 2));
        setBackground(Ui.GREEN_LIGHT);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xA5D6A7)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        value.setFont(value.getFont().deriveFont(Font.BOLD, 22f));
        value.setForeground(Ui.GREEN_DARK);
        JLabel c = new JLabel(caption);
        c.setForeground(Ui.GREY_TEXT);
        delta.setFont(delta.getFont().deriveFont(Font.PLAIN, 12f));
        JPanel south = new JPanel(new GridLayout(2, 1));
        south.setOpaque(false);
        south.add(c);
        south.add(delta);
        add(value, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    public void set(String text) { value.setText(text); delta.setText(" "); }

    /** Shows the change vs the previous period, e.g. "+25% vs previous 30 days". */
    public void set(String text, double current, double previous, int days) {
        value.setText(text);
        if (previous <= 0 && current <= 0) { delta.setText("no activity"); delta.setForeground(Palette.NEUTRAL); return; }
        if (previous <= 0) { delta.setText("new vs previous " + days + " days"); delta.setForeground(Palette.GOOD); return; }
        double pct = (current - previous) / previous * 100.0;
        String arrow = pct > 0.5 ? "▲ " : pct < -0.5 ? "▼ " : "= ";
        delta.setText(String.format("%s%+.0f%% vs previous %d days", arrow, pct, days));
        delta.setForeground(pct > 0.5 ? Palette.GOOD : pct < -0.5 ? Palette.BAD : Palette.NEUTRAL);
    }
}
