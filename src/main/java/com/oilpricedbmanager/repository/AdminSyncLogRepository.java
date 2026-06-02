package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.dto.AdminSyncLogItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class AdminSyncLogRepository {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public AdminSyncLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminSyncLogItem> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = """
                SELECT log_type,
                       ref_id,
                       sector_id,
                       sector_code,
                       sync_type,
                       fuel_type,
                       opinet_prodcd,
                       status,
                       station_count,
                       detail,
                       started_at,
                       finished_at
                FROM (
                    SELECT 'BATCH' AS log_type,
                           sync_id AS ref_id,
                           NULL::bigint AS sector_id,
                           NULL::text AS sector_code,
                           sync_type,
                           NULL::text AS fuel_type,
                           NULL::text AS opinet_prodcd,
                           status,
                           NULL::integer AS station_count,
                           COALESCE(message, '') AS detail,
                           started_at,
                           finished_at
                    FROM sync_log
                    UNION ALL
                    SELECT 'SECTOR' AS log_type,
                           ssl.sync_log_id AS ref_id,
                           ssl.sector_id,
                           ss.sector_code,
                           NULL::text AS sync_type,
                           ssl.fuel_type,
                           ssl.opinet_prodcd,
                           ssl.status,
                           ssl.station_count,
                           COALESCE(CASE
                                        WHEN ssl.status = 'SUCCESS' THEN 'stations=' || ssl.station_count
                                        ELSE ssl.error_message
                                    END, '') AS detail,
                           ssl.started_at,
                           ssl.finished_at
                    FROM sector_sync_log ssl
                    JOIN sync_sector ss ON ss.sector_id = ssl.sector_id
                ) logs
                ORDER BY started_at DESC, ref_id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminSyncLogItem(
                rs.getString("log_type"),
                rs.getLong("ref_id"),
                rs.getObject("sector_id") == null ? null : rs.getLong("sector_id"),
                rs.getString("sector_code"),
                rs.getString("sync_type"),
                rs.getString("fuel_type"),
                rs.getString("opinet_prodcd"),
                rs.getString("status"),
                rs.getObject("station_count") == null ? null : rs.getInt("station_count"),
                rs.getString("detail"),
                formatTimestamp(rs.getTimestamp("started_at")),
                formatTimestamp(rs.getTimestamp("finished_at"))
        ), safeLimit);
    }

    private static String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.format(FORMATTER);
    }
}
