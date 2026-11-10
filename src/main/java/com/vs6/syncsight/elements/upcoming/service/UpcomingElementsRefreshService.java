package com.vs6.syncsight.elements.upcoming.service;

import com.vs6.syncsight.elements.shared.api.ElementCountClient;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.sync.AbstractElementsRefreshService;
import com.vs6.syncsight.shared.auth.AuthTokenService;
import com.vs6.syncsight.shared.store.StoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Elements > Upcoming: the promos that start after today. */
@Service
public class UpcomingElementsRefreshService extends AbstractElementsRefreshService {

    private final UpcomingElementPromotionService promoService;

    public UpcomingElementsRefreshService(
            UpcomingElementPromotionService promoService,
            StoreService storeService,
            ElementCountClient client,
            AuthTokenService authTokenService,
            @Value("${vs6-enterprise.api.base-url}") String enterpriseUrl,
            @Value("${vs6-enterprise.elements.threads:24}") int threads) {
        super("Elements/Upcoming", storeService, client, authTokenService, enterpriseUrl, threads);
        this.promoService = promoService;
    }

    @Override
    protected List<ElementPromotion> loadPromos() {
        return promoService.getUpcoming();
    }
}