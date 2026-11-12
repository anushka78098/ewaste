package com.ewaste.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/** Main window: green header bar (user, points, unread) and one tab per page. */
public class MainFrame extends JFrame {
    private final App app;
    private final JLabel who = new JLabel();
    private final JLabel stats = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<Page> pages;

    public MainFrame(App app, List<Page> pages, Runnable onLogout) {
        super("E-Waste Manager - " + (app.isAdmin() ? "Admin" : app.isRecycler() ? "Recycler" : "User"));
        this.app = app;
        this.pages = pages;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Ui.GREEN);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        who.setForeground(Color.WHITE);
        who.setFont(who.getFont().deriveFont(Font.BOLD, 15f));
        stats.setForeground(new Color(0xE8F5E9));
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(who);
        left.add(stats);
        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> { app.api.logout(); dispose(); onLogout.run(); });
        header.add(left, BorderLayout.WEST);
        header.add(logout, BorderLayout.EAST);

        for (Page p : pages) tabs.addTab(p.title(), p);
        tabs.addChangeListener(e -> {
            int i = tabs.getSelectedIndex();
            if (i >= 0) pages.get(i).refresh();
            refreshHeader();
        });

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        app.refreshHeader = this::refreshHeader;
        app.goTo = this::select;
        for (Page p : pages) if (p instanceof MarketMessagesPage) {
            MarketMessagesPage messages = (MarketMessagesPage) p;
            app.openConversation = messages::openConversation;
        }

        // light polling so new notifications / points show up without clicking around
        new Timer(30_000, e -> refreshHeader()).start();
        refreshHeader();
        if (!pages.isEmpty()) pages.get(0).refresh();
    }

    /** Shows the tab with this title, if there is one. */
    public void select(String title) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).title().equals(title)) { tabs.setSelectedIndex(i); return; }
    }

    private void refreshHeader() {
        // silent: a background poll must not pop up an error dialog every 30s if the server is down
        new SwingWorker<Map<String, Object>, Void>() {
            @Override protected Map<String, Object> doInBackground() { return ApiClient.obj(app.api.get("/api/me")); }
            @Override protected void done() {
                try { apply(get()); } catch (Exception ignored) { stats.setText("Server not reachable"); }
            }
        }.execute();
    }

    private void apply(Map<String, Object> me) {
        {
            app.user = me;
            who.setText(Ui.s(me.get("fullName")) + "  (" + Ui.s(me.get("username")) + ")");
            if (app.isAdmin()) {
                stats.setText("Administrator");
            } else {
                int unread = Ui.i(me.get("unread"));
                stats.setText((app.isRecycler() ? "Recycler   |   " : "")
                        + Ui.i(me.get("points")) + " reward points   |   "
                        + (unread == 0 ? "no new notifications" : unread + " unread notification" + (unread > 1 ? "s" : "")));
            }
        }
    }
}
