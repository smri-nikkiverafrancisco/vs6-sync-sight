package com.vs6.syncsight.promotions.shared.snapshot;

import com.vs6.syncsight.shared.source.FetchResult;
import com.vs6.syncsight.shared.source.Source;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromotionSnapshotStore {

    private final Map<String, FetchResult> album = new ConcurrentHashMap<>();

    private String key(Source source, int storeId) {
        return source + ":" + storeId;
    }

    public void save(Source source, int storeId, FetchResult result) {
        album.put(key(source, storeId), result);
    }

    public Optional<FetchResult> get(Source source, int storeId) {
        return Optional.ofNullable(album.get(key(source, storeId)));
    }
}