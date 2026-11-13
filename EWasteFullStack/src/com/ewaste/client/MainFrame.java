package com.ewaste.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

/** Main workspace with role-aware side navigation and a live account header. */
public class MainFrame extends JFrame {
    private static final Color SIDEBAR = new Color(0x082B5B);
    private static final Color SIDEBAR_ACTIVE = new Color(0x125BA7);
    private static final Color APP_BACKGROUND = new Color(0xF4F7FB);

    private final App app;
    private final JLabel who = new JLabel();
    private final JLabel stats = new JLabel();
    private final JLabel pageTitle = new JLabel();
    private final JList<String> navigation = new JList<>();
    private final JPanel cards = new JPanel(new CardLayout());
    private final CardLayout cardLayout = (CardLayout) cards.getLayout();
    private final List<Page> pages;

    public MainFrame(App app, List<Page> pages, Runnable onLogout) {
        this(app, pages, onLogout, null);
    }

    public MainFrame(App app, List<Page> pages, Runnable onLogout, String initialPage) {
        super("E-Waste Manager - " + (initialPage != null ? initialPage.replace(" dashboard", "")
                : app.isAdmin() ? "Admin" : app.isRecycler() ? "Recycler" : "User"));
        this.app = app;
        this.pages = pages;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1040, 620));
        setLocationRelativeTo(null);

        DefaultListModel<String> menu = new DefaultListModel<>();
        for (Page page : pages) {
            menu.addElement(page.title());
            cards.add(page, page.title());
        }
        navigation.setModel(menu);
        navigation.setName("main-navigation");
        navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navigation.setFixedCellHeight(42);
        navigation.setBackground(SIDEBAR);
        navigation.setForeground(new Color(0xE0ECE4));
        navigation.setBorder(new EmptyBorder(8, 8, 8, 8));
        navigation.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                      boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.setOpaque(true);
                label.setBorder(new EmptyBorder(0, 14, 0, 10));
                label.setBackground(selected ? SIDEBAR_ACTIVE : SIDEBAR);
                label.setForeground(selected ? Color.WHITE : new Color(0xD0DED5));
                label.setFont(label.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN, 13f));
                return label;
            }
        });

        JPanel sidebar = createSidebar(app);
        cards.setBackground(APP_BACKGROUND);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(APP_BACKGROUND);
        workspace.add(createHeader(onLogout), BorderLayout.NORTH);
        workspace.add(cards, BorderLayout.CENTER);

        add(sidebar, BorderLayout.WEST);
        add(workspace, BorderLayout.CENTER);

        navigation.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showPage(navigation.getSelectedIndex());
        });

        app.refreshHeader = this::refreshHeader;
        app.goTo = this::select;
        for (Page page : pages) if (page instanceof MarketMessagesPage) {
            MarketMessagesPage messages = (MarketMessagesPage) page;
            app.openConversation = messages::openConversation;
        }

        int initialIndex = 0;
        if (initialPage != null) {
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).title().equals(initialPage)) {
                    initialIndex = i;
                    break;
                }
            }
        }
        if (!pages.isEmpty()) navigation.setSelectedIndex(initialIndex);
        new Timer(30_000, e -> refreshHeader()).start();
        refreshHeader();
    }

    private JPanel createSidebar(App app) {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(238, 0));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(22, 18, 20, 12));
        JLabel mark = new JLabel("EW", SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setForeground(Color.WHITE);
        mark.setBackground(new Color(0x08A5C7));
        mark.setFont(mark.getFont().deriveFont(Font.BOLD, 15f));
        mark.setPreferredSize(new Dimension(40, 40));
        JLabel name = new JLabel("E-WASTE");
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
        JLabel caption = new JLabel("CIRCULAR OPERATIONS");
        caption.setForeground(new Color(0xB9D2EA));
        caption.setFont(caption.getFont().deriveFont(Font.PLAIN, 9f));
        JPanel brandText = new JPanel(new GridLayout(2, 1, 0, 2));
        brandText.setOpaque(false);
        brandText.add(name);
        brandText.add(caption);
        brand.add(mark, BorderLayout.WEST);
        brand.add(brandText, BorderLayout.CENTER);

        JScrollPane menu = new JScrollPane(navigation);
        menu.setBorder(null);
        menu.setOpaque(false);
        menu.getViewport().setOpaque(false);
        menu.getVerticalScrollBar().setUnitIncrement(16);

        JLabel role = new JLabel(app.isAdmin() ? "ADMIN WORKSPACE"
                : app.isRecycler() ? "RECYCLER WORKSPACE" : "MEMBER WORKSPACE");
        role.setForeground(new Color(0xB9D2EA));
        role.setFont(role.getFont().deriveFont(Font.BOLD, 10f));
        role.setBorder(new EmptyBorder(14, 20, 20, 12));

        sidebar.add(brand, BorderLayout.NORTH);
        sidebar.add(menu, BorderLayout.CENTER);
        sidebar.add(role, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel createHeader(Runnable onLogout) {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2E9E4)),
                new EmptyBorder(12, 22, 12, 20)));

        JLabel section = new JLabel("YOUR WORKSPACE");
        section.setForeground(Ui.GREY_TEXT);
        section.setFont(section.getFont().deriveFont(Font.BOLD, 9f));
        pageTitle.setForeground(new Color(0x20342A));
        pageTitle.setFont(pageTitle.getFont().deriveFont(Font.BOLD, 18f));
        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(section);
        titleBlock.add(pageTitle);

        who.setForeground(new Color(0x34483B));
        who.setFont(who.getFont().deriveFont(Font.BOLD, 12f));
        stats.setOpaque(true);
        stats.setForeground(Ui.GREEN_DARK);
        stats.setBackground(Ui.GREEN_LIGHT);
        stats.setBorder(new EmptyBorder(7, 10, 7, 10));
        stats.setFont(stats.getFont().deriveFont(Font.BOLD, 11f));
        JButton logout = Ui.button("Log out", () -> {
            app.api.logout();
            dispose();
            onLogout.run();
        });
        JPanel account = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        account.setOpaque(false);
        account.add(who);
        account.add(stats);
        account.add(logout);

        header.add(titleBlock, BorderLayout.WEST);
        header.add(account, BorderLayout.EAST);
        return header;
    }

    private void showPage(int index) {
        if (index < 0 || index >= pages.size()) return;
        Page page = pages.get(index);
        pageTitle.setText(page.title());
        cardLayout.show(cards, page.title());
        page.refresh();
    }

    /** Shows the page with this title, if there is one. */
    public void select(String title) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).title().equals(title)) {
                if (navigation.getSelectedIndex() == i) showPage(i);
                else navigation.setSelectedIndex(i);
                return;
            }
        }
    }

    private void refreshHeader() {
        new SwingWorker<Map<String, Object>, Void>() {
            @Override protected Map<String, Object> doInBackground() { return ApiClient.obj(app.api.get("/api/me")); }
            @Override protected void done() {
                try { apply(get()); } catch (Exception ignored) { stats.setText("Server not reachable"); }
            }
        }.execute();
    }

    private void apply(Map<String, Object> me) {
        app.user = me;
        who.setText(Ui.s(me.get("fullName")) + "  ·  " + Ui.s(me.get("username")));
        if (app.isAdmin()) {
            stats.setText("Administrator");
        } else {
            int unread = Ui.i(me.get("unread"));
            stats.setText(Ui.i(me.get("points")) + " points  ·  "
                    + (unread == 0 ? "all caught up" : unread + " unread"));
        }
    }
}
