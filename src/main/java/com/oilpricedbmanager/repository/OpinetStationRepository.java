package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.CoordinatePoint;
import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.OpinetStationPrice;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OpinetStationRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OpinetStationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertStationAndFuel(OpinetStationPrice stationPrice, FuelType fuelType, CoordinatePoint coordinatePoint) {
        upsertStation(stationPrice, coordinatePoint);
        upsertFuel(stationPrice.uniId(), fuelType, stationPrice.price());
    }

    private void upsertStation(OpinetStationPrice stationPrice, CoordinatePoint coordinatePoint) {
        String sql = """
                INSERT INTO gas_station (uni_id, poll_div_cd, os_nm, lat, lon, updated_at)
                VALUES (:uniId, :pollDivCd, :stationName, :lat, :lon, CURRENT_TIMESTAMP)
                ON CONFLICT (uni_id)
                DO UPDATE SET
                    poll_div_cd = COALESCE(EXCLUDED.poll_div_cd, gas_station.poll_div_cd),
                    os_nm = EXCLUDED.os_nm,
                    lat = EXCLUDED.lat,
                    lon = EXCLUDED.lon,
                    updated_at = CURRENT_TIMESTAMP
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uniId", stationPrice.uniId())
                .addValue("pollDivCd", stationPrice.pollDivCd())
                .addValue("stationName", stationPrice.stationName())
                .addValue("lat", coordinatePoint.lat())
                .addValue("lon", coordinatePoint.lon());
        jdbcTemplate.update(sql, params);
    }

    private void upsertFuel(String uniId, FuelType fuelType, int price) {
        String sql = switch (fuelType) {
            case REGULAR_GASOLINE -> """
                    INSERT INTO fuel (uni_id, gas_low, updated_at)
                    VALUES (:uniId, :price, CURRENT_TIMESTAMP)
                    ON CONFLICT (uni_id) DO UPDATE SET gas_low = EXCLUDED.gas_low, updated_at = CURRENT_TIMESTAMP
                    """;
            case PREMIUM_GASOLINE -> """
                    INSERT INTO fuel (uni_id, gas_hign, updated_at)
                    VALUES (:uniId, :price, CURRENT_TIMESTAMP)
                    ON CONFLICT (uni_id) DO UPDATE SET gas_hign = EXCLUDED.gas_hign, updated_at = CURRENT_TIMESTAMP
                    """;
            case DIESEL -> """
                    INSERT INTO fuel (uni_id, disl, updated_at)
                    VALUES (:uniId, :price, CURRENT_TIMESTAMP)
                    ON CONFLICT (uni_id) DO UPDATE SET disl = EXCLUDED.disl, updated_at = CURRENT_TIMESTAMP
                    """;
            case LPG -> """
                    INSERT INTO fuel (uni_id, lpg, updated_at)
                    VALUES (:uniId, :price, CURRENT_TIMESTAMP)
                    ON CONFLICT (uni_id) DO UPDATE SET lpg = EXCLUDED.lpg, updated_at = CURRENT_TIMESTAMP
                    """;
        };
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("uniId", uniId)
                .addValue("price", price));
    }
}