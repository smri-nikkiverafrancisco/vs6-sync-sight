package com.vs6.syncsight.service;

import com.vs6.syncsight.model.Store;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StoreService {

    private final JdbcTemplate jdbcTemplate;

    public StoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "Give me the list of all stores."
    public List<Store> getStores() {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT StoreId AS store_id,
                                StoreName AS store_name,
                                VS6LocalIP AS vs6_local_ip
                FROM smvs_stores
                """,
                (rs, rowNum) -> new Store(
                        rs.getInt("store_id"),
                        rs.getString("store_name"),
                        rs.getString("vs6_local_ip")));
    }
}