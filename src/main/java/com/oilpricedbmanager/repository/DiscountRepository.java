package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.Discount;
import com.oilpricedbmanager.domain.DiscountType;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.dto.DiscountResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

@Repository
public class DiscountRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DiscountRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Discount> findApplicable(String pollDivCd, FuelType fuelType, List<Long> discountIds) {
        if (discountIds == null || discountIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = """
                SELECT discount_id, poll_div_cd, discount_name, discount_type, discount_value,
                       fuel_type, is_active, starts_at, ends_at
                FROM discount
                WHERE discount_id IN (:discountIds)
                  AND is_active = true
                  AND (poll_div_cd IS NULL OR poll_div_cd = :pollDivCd)
                  AND (fuel_type IS NULL OR fuel_type = :fuelType)
                  AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
                  AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("discountIds", discountIds)
                .addValue("pollDivCd", pollDivCd)
                .addValue("fuelType", fuelType.name());

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new Discount(
                rs.getLong("discount_id"),
                rs.getString("poll_div_cd"),
                rs.getString("discount_name"),
                DiscountType.valueOf(rs.getString("discount_type")),
                rs.getInt("discount_value"),
                rs.getString("fuel_type") == null ? null : FuelType.valueOf(rs.getString("fuel_type")),
                rs.getBoolean("is_active"),
                rs.getObject("starts_at", Timestamp.class) == null ? null : rs.getObject("starts_at", Timestamp.class).toLocalDateTime(),
                rs.getObject("ends_at", Timestamp.class) == null ? null : rs.getObject("ends_at", Timestamp.class).toLocalDateTime()
        ));
    }

    public List<DiscountResponse> findActive() {
        String sql = """
                SELECT discount_id, poll_div_cd, discount_name, discount_type, discount_value, fuel_type
                FROM discount
                WHERE is_active = true
                  AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
                  AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)
                ORDER BY discount_id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new DiscountResponse(
                rs.getLong("discount_id"),
                rs.getString("poll_div_cd"),
                rs.getString("discount_name"),
                DiscountType.valueOf(rs.getString("discount_type")),
                rs.getInt("discount_value"),
                rs.getString("fuel_type") == null ? null : FuelType.valueOf(rs.getString("fuel_type"))
        ));
    }
}
