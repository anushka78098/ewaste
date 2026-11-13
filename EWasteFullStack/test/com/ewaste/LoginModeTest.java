package com.ewaste;

import com.ewaste.client.LoginMode;

public final class LoginModeTest {
    private static int passed;
    private static int failed;

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }

    public static void main(String[] args) {
        check("admin mode requires the admin account", LoginMode.ADMIN.matchesRole("ADMIN")
                && !LoginMode.ADMIN.matchesRole("USER"));
        check("seller and buyer modes accept regular accounts", LoginMode.SELLER.matchesRole("USER")
                && LoginMode.BUYER.matchesRole("USER"));
        check("seller and buyer modes cannot authenticate as admin", !LoginMode.SELLER.matchesRole("ADMIN")
                && !LoginMode.BUYER.matchesRole("ADMIN"));
        check("recycler accounts retain their recycler mode", LoginMode.RECYCLER.matchesRole("RECYCLER")
                && !LoginMode.RECYCLER.matchesRole("USER"));
        check("marketplace modes open their selected dashboards",
                "Seller dashboard".equals(LoginMode.SELLER.dashboardTitle())
                        && "Buyer dashboard".equals(LoginMode.BUYER.dashboardTitle()));
        System.out.println("Login mode tests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) throw new AssertionError("Login mode tests failed");
    }
}
