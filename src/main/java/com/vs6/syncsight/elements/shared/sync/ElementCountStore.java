package com.vs6.syncsight.elements.shared.sync;

import com.vs6.syncsight.elements.shared.model.ElementCount;
import com.vs6.syncsight.shared.source.Source;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The album of element counts: one card per source (Enterprise or Local), store and promo.
// Every Elements page makes its OWN album (new ElementCountStore()).
public class ElementCountStore {

    private final Map<String, ElementCount> counts = new ConcurrentHashMap<>();

    public void save(Source source, int storeId, long promoId, ElementCount count) {
        counts.put(key(source, storeId, promoId), count);
    }

    // If there is no card, it means the count was never fetched: shown as an API error
    public ElementCount get(Source source, int storeId, long promoId) {
        return counts.getOrDefault(key(source, storeId, promoId),
                ElementCount.of(ElementCount.Status.API_ERROR));
    }

    public void clear() {
        counts.clear();
    }

    private static String key(Source source, int storeId, long promoId) {
        return source + ":" + storeId + ":" + promoId;
    }
}