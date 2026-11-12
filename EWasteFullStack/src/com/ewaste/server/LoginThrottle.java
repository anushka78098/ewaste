package com.ewaste.server;

import java.util.HashMap;
import java.util.Map;

/** Locks a username out for a while after too many failed sign-ins (slows down password guessing). */
final class LoginThrottle {
    private final int maxFailures;
    private final long lockMs;
    private final Map<String, long[]> state = new HashMap<>();   // key -> {failures, lockedUntil}

    LoginThrottle(int maxFailures, long lockMs) {
        this.maxFailures = maxFailures;
        this.lockMs = lockMs;
    }

    /** @return remaining lock time in ms, or 0 if the user may try */
    synchronized long lockedFor(String key, long now) {
        long[] s = state.get(key);
        return s == null || s[1] <= now ? 0 : s[1] - now;
    }

    synchronized void failure(String key, long now) {
        if (state.size() > 10_000) state.values().removeIf(s -> s[1] <= now && s[0] == 0);
        long[] s = state.computeIfAbsent(key, k -> new long[2]);
        if (s[1] <= now && s[1] != 0) { s[0] = 0; s[1] = 0; }       // an earlier lock has expired: start counting again
        s[0]++;
        if (s[0] >= maxFailures) { s[1] = now + lockMs; s[0] = 0; }
    }

    synchronized void success(String key) { state.remove(key); }
}
