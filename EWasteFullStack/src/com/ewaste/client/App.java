package com.ewaste.client;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Shared state for one logged-in session: the API client, the logged-in user and server metadata. */
public class App {
    public final ApiClient api;
    public Map<String, Object> user;
    public Map<String, Object> meta;
    /** Set by the main frame: refreshes the header (points, unread notifications). */
    public Runnable refreshHeader = () -> { };
    /** Set by the main frame: switches to the page with the given title (used by dashboard alerts). */
    public java.util.function.Consumer<String> goTo = t -> { };
    public BiConsumer<Integer, Integer> openConversation = (listingId, otherUserId) -> { };

    public App(ApiClient api) { this.api = api; }

    public boolean isAdmin() { return "ADMIN".equals(user.get("role")); }
    public boolean isRecycler() { return "RECYCLER".equals(user.get("role")); }
    public double pointValue() { return ((Number) meta.get("pointValue")).doubleValue(); }
    public double maxPointDiscount() { return ((Number) meta.get("maxPointDiscount")).doubleValue(); }
    public List<Map<String, Object>> categories() { return ApiClient.list(meta.get("categories")); }
    public List<Map<String, Object>> conditions() { return ApiClient.list(meta.get("conditions")); }
    public List<Map<String, Object>> decisions() { return ApiClient.list(meta.get("decisions")); }
    public List<Map<String, Object>> statuses() { return ApiClient.list(meta.get("statuses")); }

    public void loadMeta() { meta = ApiClient.obj(api.get("/api/meta")); }
    public void reloadUserQuiet() { try { reloadUser(); } catch (RuntimeException ignored) { } }
    public void reloadUser() { user = ApiClient.obj(api.get("/api/me")); }
}
