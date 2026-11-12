package com.ewaste.server;

/** Unit test for the sign-in lockout (package-private class, so this test lives in the same package). */
public class ThrottleTest {
    static int failed;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + name);
        if (!ok) failed++;
    }

    public static void main(String[] args) {
        LoginThrottle t = new LoginThrottle(3, 60_000);
        long now = 1_000_000;
        check("fresh user is not locked", t.lockedFor("a", now) == 0);
        t.failure("a", now); t.failure("a", now);
        check("two failures: still allowed", t.lockedFor("a", now) == 0);
        t.failure("a", now);
        check("third failure locks for the full period", t.lockedFor("a", now) == 60_000);
        check("lock counts down", t.lockedFor("a", now + 45_000) == 15_000);
        check("other users are unaffected", t.lockedFor("b", now) == 0);
        check("lock expires", t.lockedFor("a", now + 60_001) == 0);
        t.failure("a", now + 61_000);
        check("after expiry the counter starts again (1 failure, not locked)", t.lockedFor("a", now + 61_000) == 0);
        t.failure("a", now + 61_000); t.failure("a", now + 61_000);
        check("and can lock again", t.lockedFor("a", now + 61_000) > 0);
        t.success("a");
        check("success clears the lock state", t.lockedFor("a", now + 61_000) == 0);
        System.out.println("\n" + (failed == 0 ? "all passed" : failed + " failed"));
        System.exit(failed == 0 ? 0 : 1);
    }
}
