package com.vs6.syncsight.promotions.shared.snapshot;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The progress board of one refresh: which stage it is in and how many stores are done.
 * The refresh writes to it, the page reads from it.
 */
public class RefreshProgress {

    private final AtomicInteger storesDone = new AtomicInteger();
    private volatile int storesTotal;
    private volatile String stage = "Idle";

    // Start a new refresh with a clean board
    public void reset() {
        storesDone.set(0);
        storesTotal = 0;
    }

    public void setStage(String stage) { this.stage = stage; }

    public String getStage() { return stage; }

    public void setTotal(int total) { this.storesTotal = total; }

    public int getTotal() { return storesTotal; }

    // One more store is finished; returns how many are done now
    public int storeDone() { return storesDone.incrementAndGet(); }

    public int getDone() { return storesDone.get(); }
}