package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.FuelPriceCsvRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class FuelPriceImportRepository {
    private static final int LOOKUP_CHUNK_SIZE = 1_000;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FuelPriceImportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> findExistingStationIds(Collection<String> uniIds) {
        return findExistingIds("gas_station", uniIds);
    }

    public Set<String> findExistingFuelIds(Collection<String> uniIds) {
        return findExistingIds("fuel", uniIds);
    }

    public int upsertFuelPrices(List<FuelPriceCsvRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO fuel (uni_id, gas_hign, gas_low, disl, updated_at)
                VALUES (:uniId, :gasHign, :gasLow, :disl, CURRENT_TIMESTAMP)
                ON CONFLICT (uni_id) DO UPDATE SET
                    gas_hign = EXCLUDED.gas_hign,
                    gas_low = EXCLUDED.gas_low,
                    disl = EXCLUDED.disl,
                    updated_at = CURRENT_TIMESTAMP
                """;

        MapSqlParameterSource[] batch = records.stream()
                .map(record -> new MapSqlParameterSource()
                        .addValue("uniId", record.uniId())
                        .addValue("gasHign", record.premiumGasolineWon())
                        .addValue("gasLow", record.regularGasolineWon())
                        .addValue("disl", record.dieselWon()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, batch);
        return records.size();
    }

    private Set<String> findExistingIds(String tableName, Collection<String> uniIds) {
        List<String> ids = new ArrayList<>(uniIds);
        Set<String> existingIds = new HashSet<>();
        for (int start = 0; start < ids.size(); start += LOOKUP_CHUNK_SIZE) {
            List<String> chunk = ids.subList(start, Math.min(start + LOOKUP_CHUNK_SIZE, ids.size()));
            if (chunk.isEmpty()) {
                continue;
            }
            String sql = "SELECT uni_id FROM " + tableName + " WHERE uni_id IN (:uniIds)";
            existingIds.addAll(jdbcTemplate.queryForList(sql, new MapSqlParameterSource("uniIds", chunk), String.class));
        }
        return existingIds;
    }
}