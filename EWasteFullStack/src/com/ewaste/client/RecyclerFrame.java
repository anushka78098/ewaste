package com.ewaste.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recycler workspace, retaining the usual member screens alongside recycler auctions. */
public final class RecyclerFrame {
    private RecyclerFrame() {}

    public static MainFrame create(App app, Runnable onLogout) {
        return create(app, onLogout, null);
    }

    public static MainFrame create(App app, Runnable onLogout, String initialPage) {
        List<Page> pages = new ArrayList<>(Arrays.asList(new UserDashboardPage(app), new MarketDashboardPage(app, false),
                new MarketDashboardPage(app, true), new SubmitPage(app), new MyItemsPage(app), new MarketPage(app),
                new MarketMessagesPage(app), new SupportMessagesPage(app, false), new OrdersPage(app), new NotificationsPage(app), new ImpactPage(app),
                new EcoChallengesPage(app), new ProfilePage(app)));
        pages.add(new RecyclerAuctionsPage(app));
        return new MainFrame(app, pages, onLogout, initialPage);
    }
}
