package com.taskqueue.util;

import java.util.concurrent.ThreadLocalRandom;

public class RetryDelayCalculator {
    public static long calculateDelay(int retryCount, long baseDelay, long maxDelay) {
        long delay = baseDelay * (long) Math.pow(2, retryCount);
        // Add ±20% jitter
        double jitter = 0.8 + (0.4 * ThreadLocalRandom.current().nextDouble());
        delay = (long) (delay * jitter);
        return Math.min(delay, maxDelay);
    }
}
