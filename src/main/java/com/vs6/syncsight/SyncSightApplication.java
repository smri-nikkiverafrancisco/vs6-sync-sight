package com.vs6.syncsight;

import com.vs6.syncsight.model.Source;
import com.vs6.syncsight.model.StorePromotion;
import com.vs6.syncsight.model.Store;
import com.vs6.syncsight.service.AuthTokenService;
import com.vs6.syncsight.service.StorePromotionService;
import com.vs6.syncsight.service.PromotionSnapshotStore;
import com.vs6.syncsight.service.StoreService;
import com.vs6.syncsight.service.StartingPromotionService;
import com.vs6.syncsight.model.StartingPromotion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.vs6.syncsight.service.PromotionSyncService;
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
	CommandLineRunner refreshOnStart(PromotionSyncService syncService) {
		return args -> {
			try {
				syncService.refresh();
			} catch (Exception e) {
				log.error("Refresh failed: {}", e.getMessage());
			}
		};
	}
}