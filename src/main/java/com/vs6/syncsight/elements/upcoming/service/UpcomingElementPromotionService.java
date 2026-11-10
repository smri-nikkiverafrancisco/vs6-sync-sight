package com.vs6.syncsight.elements.upcoming.service;

import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class UpcomingElementPromotionService {

    private final JdbcTemplate jdbcTemplate;

    public UpcomingElementPromotionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "Which promos start after today, for which stores, and how many elements does the DATABASE say they have?"
    public List<ElementPromotion> getUpcoming() {
        return jdbcTemplate.query(
                """
                WITH UpcomingPromotions AS (
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
                        ON p.XMLPromoId = pr.PromoId
                    INNER JOIN promotypes pt
                        ON pr.PromoTypeId = pt.PromoTypeId AND LOWER(pt.TypeDesc) NOT LIKE '%no pos scanning%'
                    WHERE CAST(p.ActiveDateTo AS DATE) >= CAST(p.ActiveDateFrom AS DATE)
                      AND CAST(p.ActiveDateFrom AS DATE) = CAST(DATEADD(DAY, 1, GETDATE()) AS DATE)
                ),
                PromotionElements AS (
                    SELECT e.PromoID, COUNT(e.ElementID) AS element_count
                    FROM smvs_promotion_elements e
                    WHERE e.PromoID IN (SELECT PromoID FROM UpcomingPromotions)
                    GROUP BY e.PromoID
                )
                SELECT
                    up.PromoID AS promo_id,
                    up.MemoNumber AS memo_number,
                    up.ActiveDateFrom AS start_date,
                    up.ActiveDateTo AS end_date,
                    COUNT(DISTINCT up.StoreID) AS store_count,
                    STRING_AGG(CAST(up.StoreID AS varchar(max)),',') WITHIN GROUP (ORDER BY up.StoreID) AS store_ids,
                    COALESCE(MAX(pe.element_count), 0) AS element_count,
                    up.total_days AS promo_duration_days,
                    CONCAT_WS(', ',
                        CASE
                            WHEN up.total_days / 30 = 1 THEN '1 month'
                            WHEN up.total_days / 30 > 1 THEN CONCAT(up.total_days / 30, ' months')
                        END,
                        CASE
                            WHEN (up.total_days % 30) / 7 = 1 THEN '1 week'
                            WHEN (up.total_days % 30) / 7 > 1 THEN CONCAT((up.total_days % 30) / 7, ' weeks')
                        END,
                        CASE
                            WHEN (up.total_days % 30) % 7 = 1 THEN '1 day'
                            WHEN (up.total_days % 30) % 7 > 1 THEN CONCAT((up.total_days % 30) % 7, ' days')
                        END
                    ) AS promo_duration_aggregated
                FROM UpcomingPromotions up
                LEFT JOIN PromotionElements pe ON up.PromoID = pe.PromoID
                GROUP BY
                    up.PromoID,
                    up.MemoNumber,
                    up.ActiveDateFrom,
                    up.ActiveDateTo,
                    up.total_days
                ORDER BY
                    start_date ASC, promo_duration_days ASC, store_count DESC, memo_number ASC
                """,
                (rs, rowNum) -> {
                    int days = rs.getInt("promo_duration_days");
                    List<Integer> storeIds = Arrays.stream(rs.getString("store_ids").split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .toList();
                    return new ElementPromotion(
                            rs.getInt("promo_id"),
                            rs.getString("memo_number"),
                            rs.getDate("start_date").toLocalDate(),
                            rs.getDate("end_date").toLocalDate(),
                            rs.getInt("store_count"),
                            storeIds,
                            rs.getLong("element_count"),
                            days,
                            days <= 0 ? "0 Day" : rs.getString("promo_duration_aggregated"));
                });
    }
}