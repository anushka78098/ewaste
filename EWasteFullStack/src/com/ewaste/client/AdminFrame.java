package com.ewaste.client;

import java.util.Arrays;
import java.util.List;

public final class AdminFrame {
    private AdminFrame() {}

    public static MainFrame create(App app, Runnable onLogout) {
        List<Page> pages = Arrays.asList(new DashboardPage(app), new MarketDashboardPage(app, false), new MarketDashboardPage(app, true),
                new ReviewPage(app), new InventoryPage(app), new MarketAdminPage(app), new AuctionAdminPage(app), new MarketMessagesPage(app),
                new RecyclingPage(app), new CampaignsPage(app), new AllItemsPage(app), new UsersPage(app), new AnnouncePage(app), new ProfilePage(app));
        return new MainFrame(app, pages, onLogout);
    }
}
