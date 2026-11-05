package com.vs6.syncsight.elements.ongoing.starting.service;

import com.vs6.syncsight.elements.shared.api.ElementCountClient;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.sync.AbstractElementsRefreshService;
import com.vs6.syncsight.shared.auth.AuthTokenService;
import com.vs6.syncsight.shared.store.StoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Elements > Ongoing > Starting: the promos that start today. */
@Service
public class StartingElementsRefreshService extends AbstractElementsRefreshService {

    private final StartingElementPromotionService promoService;

    public StartingElementsRefreshService(
            StartingElementPromotionService promoService,
            StoreService storeService,
            ElementCountClient client,
            AuthTokenService authTokenService,
            @Value("${vs6-enterprise.api.base-url}") String enterpriseUrl,
            @Value("${vs6-enterprise.elements.threads:24}") int threads) {
        super("Elements/Starting", storeService, client, authTokenService, enterpriseUrl, threads);
        this.promoService = promoService;
    }

    @Override
    protected List<ElementPromotion> loadPromos() {
        return promoService.getStartingToday();
    }
}