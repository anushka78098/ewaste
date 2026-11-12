package com.ewaste.server;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** In-memory bearer-token sessions (restarting the server logs everyone out). */
public class Sessions {
    private static final long TTL_MS = 12L * 60 * 60 * 1000;

    private static final class S {
        final int userId;
        long expires;
        S(int userId, long expires) { this.userId = userId; this.expires = expires; }
    }

    private final Map<String, S> map = new HashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    public synchronized String create(int userId) {
        byte[] b = new byte[24];
        rnd.nextBytes(b);
        String t = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        map.put(t, new S(userId, System.currentTimeMillis() + TTL_MS));
        return t;
    }

    /** @return the user id, or -1 if the token is unknown or expired */
    public synchronized int userId(String token) {
        S s = token == null ? null : map.get(token);
        if (s == null) return -1;
        long now = System.currentTimeMillis();
        if (s.expires < now) { map.remove(token); return -1; }
        s.expires = now + TTL_MS;
        return s.userId;
    }

    public synchronized void remove(String token) { map.remove(token); }
}
