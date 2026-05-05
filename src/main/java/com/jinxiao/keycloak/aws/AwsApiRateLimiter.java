package com.jinxiao.keycloak.aws;

interface AwsApiLimiter {
    void acquire();
}

final class AwsApiRateLimiter implements AwsApiLimiter {
    private final long intervalNanos;
    private long nextAllowedNanos;

    private AwsApiRateLimiter(int maxQps) {
        this.intervalNanos = 1_000_000_000L / maxQps;
        this.nextAllowedNanos = System.nanoTime();
    }

    static AwsApiRateLimiter create(int maxQps) {
        if (maxQps <= 0) {
            throw new IllegalArgumentException("maxQps must be > 0");
        }
        return new AwsApiRateLimiter(maxQps);
    }

    @Override
    public synchronized void acquire() {
        long now = System.nanoTime();
        long waitNanos = nextAllowedNanos - now;
        if (waitNanos > 0) {
            sleep(waitNanos);
            now = System.nanoTime();
        }
        nextAllowedNanos = Math.max(now, nextAllowedNanos) + intervalNanos;
    }

    private void sleep(long waitNanos) {
        long millis = waitNanos / 1_000_000L;
        int nanos = (int) (waitNanos % 1_000_000L);
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for AWS API rate limit", e);
        }
    }
}
