package com.vs6.syncsight;

import com.vs6.syncsight.model.Source;
import com.vs6.syncsight.model.StorePromotion;
import com.vs6.syncsight.model.Store;
import com.vs6.syncsight.service.AuthTokenService;
import com.vs6.syncsight.service.StorePromotionService;
import com.vs6.syncsight.service.PromotionSnapshotStore;
import com.vs6.syncsight.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class SyncSightApplication {

	private static final Logger log = LoggerFactory.getLogger(SyncSightApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SyncSightApplication.class, args);
	}

	@Bean
	CommandLineRunner syncPromotions(
			StoreService storeService,
			StorePromotionService promotionService,
			AuthTokenService authTokenService,
			PromotionSnapshotStore snapshotStore,
			@Value("${vs6-enterprise.api.base-url}") String enterpriseUrl) {
		return args -> {
			log.info("Reading stores from SQL Server...");
			List<Store> stores;
			try {
				stores = storeService.getStores();
			} catch (Exception e) {
				log.error("Cannot read stores from SQL Server: {}", e.getMessage());
				return;   // no store list means nothing to do, so stop here
			}
			log.info("Found {} stores", stores.size());

			for (Store store : stores) {
				log.info("--- Store {} ({}) ---", store.storeId(), store.storeName());

				String enterpriseCount = fetchCount(promotionService, authTokenService, snapshotStore,
						Source.ENTERPRISE, enterpriseUrl, store.storeId());

				String localCount;
				if (store.localIp() == null || store.localIp().isBlank()) {
					localCount = "no local IP";
				} else {
					String localUrl = "http://visualstore." + store.localIp().trim() + ".nip.io/vs";
					localCount = fetchCount(promotionService, authTokenService, snapshotStore,
							Source.LOCAL, localUrl, store.storeId());
				}

				log.info("Store {} : Enterprise = {} | Local = {}",
						store.storeId(), enterpriseCount, localCount);
			}

			snapshotStore.get(Source.ENTERPRISE, 2).ifPresent(s ->
					log.info("Enterprise store 2: {} promos, taken at {}", s.promotions().size(), s.fetchedAt()));
			snapshotStore.get(Source.LOCAL, 2).ifPresent(s ->
					log.info("Local store 2: {} promos, taken at {}", s.promotions().size(), s.fetchedAt()));

			log.info("Done.");
		};
	}

	// Asks one address for one store's promotions. Returns the count, or a short error note.
	private String fetchCount(StorePromotionService promotionService,
							  AuthTokenService authTokenService,
							  PromotionSnapshotStore snapshotStore,
							  Source source, String baseUrl, int storeId) {
		try {
			List<StorePromotion> promos = promotionService.getPromotions(
					baseUrl, authTokenService.getAccessToken(baseUrl), storeId);
			snapshotStore.save(source, storeId, promos);     // NEW: keep the photo
			return String.valueOf(promos.size());
		} catch (IllegalStateException e) {
			log.error(e.getMessage());
			return "ERROR";
		}
	}
}