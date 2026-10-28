package com.vs6.syncsight.promotions.ongoing.all.service;

import com.vs6.syncsight.promotions.shared.model.Promotion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class AllOngoingPromotionService {

    private final JdbcTemplate jdbcTemplate;

    public AllOngoingPromotionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "Which promos start today, and for which stores?"
    public List<Promotion> getAllOngoing() {
        return jdbcTemplate.query(
                """
                WITH OngoingPromotions AS (
                    SELECT
                        p.PromoID,
                        p.MemoNumber,
                        p.ActiveDateFrom,
                        p.ActiveDateTo,
                        ps.StoreID,
                        DATEDIFF(day, p.ActiveDateFrom, p.ActiveDateTo) + 1 AS total_days
                    FROM smvs_promotions p
                    INNER JOIN smvs_promotion_stores ps
                        ON p.PromoID = ps.PromoId
                    INNER JOIN promotions pr
                        ON p.XMLPromoId = pr.promoid
                    INNER JOIN promotypes pt
                        ON pr.promotypeid = pt.promotypeid AND LOWER(pt.TypeDesc) NOT LIKE '%no pos scanning%'
                    WHERE CAST(GETDATE() AS DATE) BETWEEN CAST(p.ActiveDateFrom AS DATE) AND CAST(p.ActiveDateTo AS DATE)
                    AND CAST(p.ActiveDateTo AS DATE) >= CAST(p.ActiveDateFrom AS DATE)
                )
                SELECT
                    PromoID AS promo_id,
                    MemoNumber AS memo_number,
                    ActiveDateFrom AS start_date,
                    ActiveDateTo AS end_date,
                    COUNT(DISTINCT StoreID) AS store_count,
                    STRING_AGG(CAST(StoreID AS varchar(max)),',') WITHIN GROUP (ORDER BY StoreID) AS store_ids,
                    total_days AS promo_duration_days,
                    CONCAT_WS(', ',
                        CASE
                            WHEN total_days / 30 = 1 THEN '1 month'
                            WHEN total_days / 30 > 1 THEN CONCAT(total_days / 30, ' months')
                        END,
                        CASE
                            WHEN (total_days % 30) / 7 = 1 THEN '1 week'
                            WHEN (total_days % 30) / 7 > 1 THEN CONCAT((total_days % 30) / 7, ' weeks')
                        END,
                        CASE
                            WHEN (total_days % 30) % 7 = 1 THEN '1 day'
                            WHEN (total_days % 30) % 7 > 1 THEN CONCAT((total_days % 30) % 7, ' days')
                        END
                    ) AS promo_duration_aggregated
                FROM OngoingPromotions
                GROUP BY
                    PromoID,
                    MemoNumber,
                    ActiveDateFrom,
                    ActiveDateTo,
                    total_days
                ORDER BY
                    promo_duration_days ASC, store_count DESC, memo_number ASC
                """,
                (rs, rowNum) -> {
                    int days = rs.getInt("promo_duration_days");
                    List<Integer> storeIds = Arrays.stream(rs.getString("store_ids").split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .toList();
                    return new Promotion(
                            rs.getInt("promo_id"),
                            rs.getString("memo_number"),
                            rs.getDate("start_date").toLocalDate(),
                            rs.getDate("end_date").toLocalDate(),
                            rs.getInt("store_count"),
                            storeIds,
                            days,
                            days <= 0 ? "0 Day" : rs.getString("promo_duration_aggregated"));
                });
    }
}