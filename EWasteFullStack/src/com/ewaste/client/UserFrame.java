package com.ewaste.client;

import java.util.Arrays;
import java.util.List;

public final class UserFrame {
    private UserFrame() {}

    public static MainFrame create(App app, Runnable onLogout) {
        List<Page> pages = Arrays.asList(new UserDashboardPage(app), new MarketDashboardPage(app, false), new MarketDashboardPage(app, true),
                new SubmitPage(app), new MyItemsPage(app), new MarketPage(app), new MarketMessagesPage(app), new OrdersPage(app),
                new NotificationsPage(app), new ImpactPage(app), new ProfilePage(app));
        return new MainFrame(app, pages, onLogout);
    }
}
