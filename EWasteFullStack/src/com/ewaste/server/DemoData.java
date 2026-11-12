package com.ewaste.server;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

/** Adds browseable marketplace inventory to a brand-new local/demo installation. */
public final class DemoData {
    private static final Category[] CATEGORIES = {
            Category.MOBILE, Category.TABLET, Category.LAPTOP, Category.DESKTOP,
            Category.TELEVISION, Category.APPLIANCE, Category.PRINTER, Category.ACCESSORY
    };
    private static final String[] BRANDS = {
            "Acer", "Apple", "Dell", "Google", "HP", "Lenovo", "LG", "Samsung", "Sony", "Xiaomi"
    };
    private static final String[] CONDITIONS = {
            "Professionally refurbished", "Tested and working", "Cleaned and inspected", "Includes power adapter"
    };

    private DemoData() {}

    public static int seedMarketplaceIfEmpty(Repository repo) {
        if (!repo.listings().isEmpty()) return 0;
        for (EWasteItem existing : repo.items())
            if (existing.description.contains("Demo marketplace listing.")) return 0;
        User seller = repo.userByName("admin");
        if (seller == null) return 0;
        for (int n = 0; n < 100; n++) {
            Category category = CATEGORIES[n % CATEGORIES.length];
            String brand = BRANDS[(n * 7) % BRANDS.length];
            EWasteItem item = new EWasteItem();
            item.ownerId = seller.id;
            item.name = category.label + " " + String.format("%03d", n + 1);
            item.brand = brand;
            item.category = category;
            item.ageYears = 1 + n % Math.max(1, Math.min(3, category.maxReuseAge));
            item.weightKg = Math.round((0.3 + (n % 35) * 0.25) * 100.0) / 100.0;
            item.condition = Condition.WORKING;
            item.description = CONDITIONS[n % CONDITIONS.length] + ". Demo marketplace listing.";
            item.status = ItemStatus.LISTED;
            item.suggestedDecision = Decision.REUSE;
            item.decision = Decision.REUSE;
            item.estimatedValue = category.baseValue;
            repo.addItem(item);

            Listing listing = new Listing();
            listing.itemId = item.id;
            listing.price = Math.round((category.baseValue * (0.35 + (n % 7) * 0.05) + 10) * 100.0) / 100.0;
            repo.addListing(listing);
        }
        return 100;
    }
}
