package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.FuelType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SectorSyncLogRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SectorSyncLogRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long start(long sectorId, FuelType fuelType) {
        String sql = """
                INSERT INTO sector_sync_log (sector_id, fuel_type, opinet_prodcd, status)
                VALUES (:sectorId, :fuelType, :opinetProdcd, 'RUNNING')
                RETURNING sync_log_id
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("sectorId", sectorId)
                .addValue("fuelType", fuelType.name())
                .addValue("opinetProdcd", fuelType.opinetProductCode()), Long.class);
    }

    public void finish(long syncLogId, String status, int stationCount, String errorMessage) {
        String sql = """
                UPDATE sector_sync_log
                SET status = :status,
                    station_count = :stationCount,
                    error_message = :errorMessage,
                    finished_at = CURRENT_TIMESTAMP
                WHERE sync_log_id = :syncLogId
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("syncLogId", syncLogId)
                .addValue("status", status)
                .addValue("stationCount", stationCount)
                .addValue("errorMessage", errorMessage));
    }
}