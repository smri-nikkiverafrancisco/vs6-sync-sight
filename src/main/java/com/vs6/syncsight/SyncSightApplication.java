package com.vs6.syncsight;

import com.vs6.syncsight.promotions.ongoing.starting.service.StartingPromotionSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;


@SpringBootApplication
public class SyncSightApplication {

	private static final Logger log = LoggerFactory.getLogger(SyncSightApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SyncSightApplication.class, args);
	}

	@Bean
	CommandLineRunner refreshOnStart(StartingPromotionSyncService syncService) {
		return args -> syncService.startRefresh();
	}
}