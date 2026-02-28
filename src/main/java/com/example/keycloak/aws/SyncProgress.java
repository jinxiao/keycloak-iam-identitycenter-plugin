
package com.example.keycloak.aws;

import java.util.concurrent.atomic.AtomicInteger;

public class SyncProgress {

    private AtomicInteger processed = new AtomicInteger(0);

    public void increment(int count) {
        processed.addAndGet(count);
    }

    public int getProcessed() {
        return processed.get();
    }
}
