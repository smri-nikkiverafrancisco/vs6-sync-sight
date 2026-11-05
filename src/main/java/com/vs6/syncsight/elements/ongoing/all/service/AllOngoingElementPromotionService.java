package com.vs6.syncsight.elements.ongoing.all.service;

import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class AllOngoingElementPromotionService {

    private final JdbcTemplate jdbcTemplate;

    public AllOngoingElementPromotionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "Which promos are ongoing today, for which stores, and how many elements does the DATABASE say they have?"
    public List<ElementPromotion> getOngoingToday() {
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
                        ON p.XMLPromoId = pr.PromoId
                    INNER JOIN promotypes pt
                        ON pr.PromoTypeId = pt.PromoTypeId AND LOWER(pt.TypeDesc) NOT LIKE '%no pos scanning%'
                    -- BETWEEN also leaves out cancelled promos (end date before start date)
                    WHERE CAST(p.ActiveDateTo AS DATE) >= CAST(p.ActiveDateFrom AS DATE)
                    AND CAST(GETDATE() AS DATE) BETWEEN CAST(p.ActiveDateFrom AS DATE) AND CAST(p.ActiveDateTo AS DATE)
                ),
                PromotionElements AS (
                    SELECT e.PromoID, COUNT(e.ElementID) AS element_count
                    FROM smvs_promotion_elements e
                    WHERE e.PromoID IN (SELECT PromoID FROM OngoingPromotions)
                    GROUP BY e.PromoID
                )
                SELECT
                    op.PromoID AS promo_id,
                    op.MemoNumber AS memo_number,
                    op.ActiveDateFrom AS start_date,
                    op.ActiveDateTo AS end_date,
                    COUNT(DISTINCT op.StoreID) AS store_count,
                    STRING_AGG(CAST(op.StoreID AS varchar(max)),',') WITHIN GROUP (ORDER BY op.StoreID) AS store_ids,
                    COALESCE(MAX(pe.element_count), 0) AS element_count,
                    op.total_days AS promo_duration_days,
                    CONCAT_WS(', ',
                        CASE
                            WHEN op.total_days / 30 = 1 THEN '1 month'
                            WHEN op.total_days / 30 > 1 THEN CONCAT(op.total_days / 30, ' months')
                        END,
                        CASE
                            WHEN (op.total_days % 30) / 7 = 1 THEN '1 week'
                            WHEN (op.total_days % 30) / 7 > 1 THEN CONCAT((op.total_days % 30) / 7, ' weeks')
                        END,
                        CASE
                            WHEN (op.total_days % 30) % 7 = 1 THEN '1 day'
                            WHEN (op.total_days % 30) % 7 > 1 THEN CONCAT((op.total_days % 30) % 7, ' days')
                        END
                    ) AS promo_duration_aggregated
                FROM OngoingPromotions op
                LEFT JOIN PromotionElements pe ON op.PromoID = pe.PromoID
                GROUP BY
                    op.PromoID,
                    op.MemoNumber,
                    op.ActiveDateFrom,
                    op.ActiveDateTo,
                    op.total_days
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