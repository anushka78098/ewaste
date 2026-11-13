package com.ewaste.client;

/** Requested workspace for a sign-in; the authenticated account role remains authoritative. */
public enum LoginMode {
    ADMIN("Admin", null),
    SELLER("Seller", "Seller dashboard"),
    BUYER("Buyer", "Buyer dashboard"),
    RECYCLER("Recycler", null);

    private final String label;
    private final String dashboardTitle;

    LoginMode(String label, String dashboardTitle) {
        this.label = label;
        this.dashboardTitle = dashboardTitle;
    }

    public boolean matchesRole(String role) {
        if (this == ADMIN) return "ADMIN".equals(role);
        if (this == RECYCLER) return "RECYCLER".equals(role);
        return "USER".equals(role) || "RECYCLER".equals(role);
    }

    public String dashboardTitle() { return dashboardTitle; }

    @Override public String toString() { return label; }
}
