package com.ewaste.server;

import com.ewaste.model.*;
import com.ewaste.server.repo.Repository;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Adds demo marketplace inventory and backfills its locally generated product images. */
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
        if (!repo.listings().isEmpty()) {
            addMissingDemoPhotos(repo);
            return 0;
        }
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
            addDemoPhoto(listing, item);
            repo.addListing(listing);
        }
        return 100;
    }

    private static void addMissingDemoPhotos(Repository repo) {
        for (Listing listing : repo.listings()) {
            EWasteItem item = repo.item(listing.itemId);
            if (listing.photoData == null && isDemoItem(item)) {
                addDemoPhoto(listing, item);
                repo.saveListing(listing);
            }
        }
    }

    private static boolean isDemoItem(EWasteItem item) {
        return item != null && item.description != null && item.description.contains("Demo marketplace listing.");
    }

    private static void addDemoPhoto(Listing listing, EWasteItem item) {
        listing.photoFileName = "demo-" + item.category.name().toLowerCase(java.util.Locale.ROOT) + ".png";
        listing.photoMediaType = "image/png";
        listing.photoData = createDemoImage(item.category, item.brand);
    }

    private static byte[] createDemoImage(Category category, String brand) {
        BufferedImage image = new BufferedImage(480, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(229, 244, 238), 480, 300, new Color(211, 229, 246)));
            g.fillRect(0, 0, 480, 300);
            g.setColor(new Color(255, 255, 255, 100));
            g.fillOval(30, 20, 105, 105);
            g.fillOval(360, 28, 90, 90);
            drawDevice(g, category);
            g.setColor(new Color(30, 58, 95));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            g.drawString(brand + " " + category.label, 24, 254);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.drawString("DEMO  |  REFURBISHED AND REUSABLE", 25, 278);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG writer is unavailable.");
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate marketplace demo image.", e);
        }
    }

    private static void drawDevice(Graphics2D g, Category category) {
        Color body = new Color(37, 61, 76);
        Color screen = new Color(116, 190, 183);
        if (category == Category.MOBILE || category == Category.TABLET) {
            int width = category == Category.MOBILE ? 86 : 132;
            int x = (480 - width) / 2;
            g.setColor(body);
            g.fill(new RoundRectangle2D.Double(x, 35, width, 180, 18, 18));
            g.setColor(screen);
            g.fill(new RoundRectangle2D.Double(x + 8, 48, width - 16, 145, 8, 8));
            g.setColor(new Color(225, 238, 234));
            g.fillOval(236, 199, 8, 8);
        } else if (category == Category.LAPTOP || category == Category.DESKTOP || category == Category.TELEVISION) {
            g.setColor(body);
            g.fillRoundRect(128, 40, 224, 132, 10, 10);
            g.setColor(screen);
            g.fillRect(140, 52, 200, 108);
            g.fillRect(226, 172, 28, 22);
            g.fillRoundRect(190, 192, 100, 10, 5, 5);
            if (category == Category.DESKTOP) {
                g.setColor(body);
                g.fillRoundRect(363, 74, 44, 120, 8, 8);
                g.setColor(new Color(127, 207, 183));
                g.fillOval(378, 88, 12, 12);
            }
        } else if (category == Category.APPLIANCE) {
            g.setColor(body);
            g.fillRoundRect(166, 35, 148, 174, 14, 14);
            g.setColor(new Color(223, 236, 234));
            g.fillRoundRect(178, 48, 124, 148, 9, 9);
            g.setColor(new Color(127, 167, 162));
            g.drawLine(248, 49, 248, 194);
            g.fillRoundRect(267, 70, 5, 23, 3, 3);
        } else if (category == Category.PRINTER) {
            g.setColor(new Color(239, 246, 243));
            g.fillRoundRect(182, 38, 116, 94, 5, 5);
            g.setColor(body);
            g.fillRoundRect(139, 90, 202, 108, 12, 12);
            g.setColor(screen);
            g.fillRoundRect(154, 105, 172, 22, 5, 5);
            g.setColor(new Color(224, 237, 233));
            g.fillRect(180, 158, 120, 24);
        } else {
            g.setColor(body);
            g.setStroke(new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(165, 42, 150, 140, 0, 180);
            g.fillRoundRect(148, 111, 40, 75, 12, 12);
            g.fillRoundRect(292, 111, 40, 75, 12, 12);
        }
    }
}
