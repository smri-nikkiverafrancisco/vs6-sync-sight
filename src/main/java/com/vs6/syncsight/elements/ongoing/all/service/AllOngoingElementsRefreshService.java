package com.vs6.syncsight.elements.ongoing.all.service;

import com.vs6.syncsight.elements.shared.api.ElementCountClient;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.sync.AbstractElementsRefreshService;
import com.vs6.syncsight.shared.auth.AuthTokenService;
import com.vs6.syncsight.shared.store.StoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Elements > Ongoing > All: the promos that are ongoing today AND started on the date the user picked.
 * It never starts by itself: it waits until a start date is picked.
 */
@Service
public class AllOngoingElementsRefreshService extends AbstractElementsRefreshService {

    private final AllOngoingElementPromotionService promoService;

    // The start date the user picked (null until one is picked)
    private volatile LocalDate startDate;

    public AllOngoingElementsRefreshService(
            AllOngoingElementPromotionService promoService,
            StoreService storeService,
            ElementCountClient client,
            AuthTokenService authTokenService,
            @Value("${vs6-enterprise.api.base-url}") String enterpriseUrl,
            @Value("${vs6-enterprise.elements.threads:24}") int threads) {
        super("Elements/AllOngoing", storeService, client, authTokenService, enterpriseUrl, threads);
        this.promoService = promoService;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    // Opening the page does NOT start a load: the user has to pick a date first
    @Override
    public void startIfNeverStarted() {
        // nothing
    }

    // The Refresh button: reload the date that is already picked (nothing to reload before that)
    @Override
    public boolean startRefresh() {
        return startDate != null && super.startRefresh();
    }

    // The Load button: "load the ongoing promos that started on this date"
    public boolean startRefresh(LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now()) || getStatus().running()) {
            return false;
        }
        if (!date.equals(startDate)) {
            clearReport();          // a different date: forget the old report right away
        }
        startDate = date;
        return super.startRefresh();
    }

    @Override
    protected List<ElementPromotion> loadPromos() {
        return promoService.getOngoingStartedOn(startDate);
    }
}