package com.mylaesoftware;


import java.time.Duration;
import java.time.temporal.ChronoUnit;

public interface Eventually {

    Duration patience = Duration.of(30L, ChronoUnit.SECONDS);

    Duration pollingInterval = Duration.of(100L, ChronoUnit.MILLIS);

    default void eventually(AssertionBlock assertion) {
        eventually(patience, pollingInterval, assertion);
    }

    default void eventually(Duration patience, Duration pollingInterval, AssertionBlock assertion) {
        long elapsed = 0L;
        boolean success = false;
        Throwable ex = null;
        do {
            try {
                assertion._assert();
                success = true;
            } catch (Throwable t) {
                try {
                    ex = t;
                    Thread.sleep(pollingInterval.toMillis());
                } catch (InterruptedException e) {}
                finally {
                    elapsed += pollingInterval.toMillis();
                }
            }

        } while (!patience.minus(elapsed, ChronoUnit.MILLIS).isNegative() && !success);
        if (!success) throw new AssertionError(ex);
    }

}
