package com.vs6.syncsight.service;

import com.vs6.syncsight.model.Source;
import com.vs6.syncsight.model.StorePromotion;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromotionSnapshotStore {

    // One photo: when it was taken + the promos in it
    public record Snapshot(Instant fetchedAt, List<StorePromotion> promotions) {}

    // The album. ConcurrentHashMap is safe if several things read/write at once.
    private final Map<String, Snapshot> album = new ConcurrentHashMap<>();

    private String key(Source source, int storeId) {
        return source + ":" + storeId;      // e.g. "ENTERPRISE:2"
    }

    // Swap in a new photo for this slot
    public void save(Source source, int storeId, List<StorePromotion> promotions) {
        album.put(key(source, storeId), new Snapshot(Instant.now(), List.copyOf(promotions)));
    }

    // Look at the photo in this slot (empty if there isn't one)
    public Optional<Snapshot> get(Source source, int storeId) {
        return Optional.ofNullable(album.get(key(source, storeId)));
    }
}