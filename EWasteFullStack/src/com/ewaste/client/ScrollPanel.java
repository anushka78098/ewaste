package com.ewaste.client;

import javax.swing.*;
import java.awt.*;

/** A panel for use as a scroll pane's view that always matches the viewport width (scrolls vertically only). */
public class ScrollPanel extends JPanel implements Scrollable {
    public ScrollPanel(LayoutManager lm) { super(lm); }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 32); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
