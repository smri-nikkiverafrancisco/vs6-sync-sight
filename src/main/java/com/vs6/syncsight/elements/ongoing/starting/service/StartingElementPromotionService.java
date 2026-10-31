package com.vs6.syncsight.elements.ongoing.starting.service;

import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class StartingElementPromotionService {

    private final JdbcTemplate jdbcTemplate;

    public StartingElementPromotionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "Which promos start today, for which stores, and how many elements does the DATABASE say they have?"
    public List<ElementPromotion> getStartingToday() {
        return jdbcTemplate.query(
                """
                WITH StartingPromotions AS (
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
                    WHERE CAST(p.ActiveDateFrom AS DATE) = CAST(GETDATE() AS DATE)
                    AND CAST(p.ActiveDateTo AS DATE) >= CAST(p.ActiveDateFrom AS DATE)
                ),
                PromotionElements AS (
                    SELECT e.PromoID, COUNT(e.ElementID) AS element_count
                    FROM smvs_promotion_elements e
                    WHERE e.PromoID IN (SELECT PromoID FROM StartingPromotions)
                    GROUP BY e.PromoID
                )
                SELECT
                    sp.PromoID AS promo_id,
                    sp.MemoNumber AS memo_number,
                    sp.ActiveDateFrom AS start_date,
                    sp.ActiveDateTo AS end_date,
                    COUNT(DISTINCT sp.StoreID) AS store_count,
                    STRING_AGG(CAST(sp.StoreID AS varchar(max)),',') WITHIN GROUP (ORDER BY sp.StoreID) AS store_ids,
                    COALESCE(MAX(pe.element_count), 0) AS element_count,
                    sp.total_days AS promo_duration_days,
                    CONCAT_WS(', ',
                        CASE
                            WHEN sp.total_days / 30 = 1 THEN '1 month'
                            WHEN sp.total_days / 30 > 1 THEN CONCAT(sp.total_days / 30, ' months')
                        END,
                        CASE
                            WHEN (sp.total_days % 30) / 7 = 1 THEN '1 week'
                            WHEN (sp.total_days % 30) / 7 > 1 THEN CONCAT((sp.total_days % 30) / 7, ' weeks')
                        END,
                        CASE
                            WHEN (sp.total_days % 30) % 7 = 1 THEN '1 day'
                            WHEN (sp.total_days % 30) % 7 > 1 THEN CONCAT((sp.total_days % 30) % 7, ' days')
                        END
                    ) AS promo_duration_aggregated
                FROM StartingPromotions sp
                LEFT JOIN PromotionElements pe ON sp.PromoID = pe.PromoID
                GROUP BY
                    sp.PromoID,
                    sp.MemoNumber,
                    sp.ActiveDateFrom,
                    sp.ActiveDateTo,
                    sp.total_days
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