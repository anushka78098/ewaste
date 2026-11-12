package com.ewaste.client;

import javax.swing.*;
import java.awt.*;

/** One tab of the main window. refresh() reloads its data from the server. */
public abstract class Page extends JPanel {
    protected final App app;
    private final String title;

    protected Page(App app, String title) {
        super(new BorderLayout(8, 8));
        this.app = app;
        this.title = title;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    public String title() { return title; }
    public abstract void refresh();

    protected JPanel top(String heading, JComponent... extra) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(Ui.heading(heading), BorderLayout.WEST);
        if (extra.length > 0) p.add(Ui.buttonRow(extra), BorderLayout.EAST);
        return p;
    }
}
