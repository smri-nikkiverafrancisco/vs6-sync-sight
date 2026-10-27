package com.vs6.syncsight;

import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
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

	// On startup: one refresh for ALL pages (Starting, All Ongoing, ...)
	@Bean
	CommandLineRunner refreshOnStart(PromotionRefreshCoordinator coordinator) {
		return args -> coordinator.startRefresh();
	}
}