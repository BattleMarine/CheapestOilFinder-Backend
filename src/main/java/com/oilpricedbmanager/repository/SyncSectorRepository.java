package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.GeneratedHexSector;
import com.oilpricedbmanager.domain.SyncSector;
import com.oilpricedbmanager.domain.SyncTier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SyncSectorRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SyncSectorRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SyncSector> findById(long sectorId) {
        String sql = selectSql() + " WHERE sector_id = :sectorId";
        List<SyncSector> sectors = jdbcTemplate.query(sql, new MapSqlParameterSource("sectorId", sectorId), (rs, rowNum) -> mapSector(rs));
        return sectors.stream().findFirst();
    }

    public List<SyncSector> findAll() {
        String sql = selectSql() + " ORDER BY priority_score DESC, sector_id ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSector(rs));
    }

    public List<SyncSector> findDueSectors(int limit) {
        return findAutoDueSectors(limit);
    }

    public List<SyncSector> findAutoDueSectors(int limit) {
        String sql = selectSql() + """
                WHERE enabled = true
                  AND sync_tier IN ('HOT', 'WARM')
                  AND next_sync_at <= CURRENT_TIMESTAMP
                ORDER BY priority_score DESC, last_synced_at ASC NULLS FIRST, sector_id ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("limit", limit), (rs, rowNum) -> mapSector(rs));
    }

    public List<SyncSector> findEnabledSectorsByTiers(List<SyncTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }

        String sql = selectSql() + """
                WHERE enabled = true
                  AND sync_tier IN (:tiers)
                ORDER BY priority_score DESC, last_synced_at ASC NULLS FIRST, sector_id ASC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("tiers", tiers.stream().map(SyncTier::name).toList()), (rs, rowNum) -> mapSector(rs));
    }

    public int upsertGeneratedSector(GeneratedHexSector sector) {
        String sql = """
                INSERT INTO sync_sector (
                    sector_code, center_lat, center_lon, katec_x, katec_y, radius_m,
                    side_length_m, sector_source, sector_shape, sync_tier, enabled,
                    priority_score, memo
                ) VALUES (
                    :sectorCode, :centerLat, :centerLon, :katecX, :katecY, :radiusMeters,
                    :sideLengthMeters, :sectorSource,
                    ST_SetSRID(ST_GeomFromText(:sectorShapeWkt), 4326),
                    :syncTier, :enabled, :priorityScore, :memo
                )
                ON CONFLICT (sector_code)
                DO UPDATE SET
                    center_lat = EXCLUDED.center_lat,
                    center_lon = EXCLUDED.center_lon,
                    katec_x = EXCLUDED.katec_x,
                    katec_y = EXCLUDED.katec_y,
                    radius_m = EXCLUDED.radius_m,
                    side_length_m = EXCLUDED.side_length_m,
                    sector_source = EXCLUDED.sector_source,
                    sector_shape = EXCLUDED.sector_shape,
                    sync_tier = EXCLUDED.sync_tier,
                    enabled = EXCLUDED.enabled,
                    priority_score = EXCLUDED.priority_score,
                    memo = EXCLUDED.memo,
                    updated_at = CURRENT_TIMESTAMP
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sectorCode", sector.sectorCode())
                .addValue("centerLat", sector.centerLat())
                .addValue("centerLon", sector.centerLon())
                .addValue("katecX", sector.katecX())
                .addValue("katecY", sector.katecY())
                .addValue("radiusMeters", sector.radiusMeters())
                .addValue("sideLengthMeters", sector.sideLengthMeters())
                .addValue("sectorSource", sector.sectorSource())
                .addValue("sectorShapeWkt", sector.sectorShapeWkt())
                .addValue("syncTier", sector.syncTier().name())
                .addValue("enabled", sector.enabled())
                .addValue("priorityScore", sector.priorityScore())
                .addValue("memo", sector.memo()));
    }

    public Optional<SyncSector> updateStatus(long sectorId, SyncTier tier) {
        boolean enabled = tier != SyncTier.DISABLED;
        String sql = """
                UPDATE sync_sector
                SET sync_tier = CASE WHEN :enabled THEN :syncTier ELSE sync_tier END,
                    enabled = :enabled,
                    priority_score = CASE WHEN :enabled THEN :priorityScore ELSE priority_score END,
                    next_sync_at = CASE
                        WHEN :enabled THEN CURRENT_TIMESTAMP
                        ELSE CURRENT_TIMESTAMP + INTERVAL '525600 minutes'
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE sector_id = :sectorId
                """;
        int updated = jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sectorId", sectorId)
                .addValue("syncTier", tier.name())
                .addValue("enabled", enabled)
                .addValue("priorityScore", priorityForTier(tier)));
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(sectorId);
    }

    public int setAllEnabled(boolean enabled) {
        String sql = """
                UPDATE sync_sector
                SET enabled = :enabled,
                    sync_tier = CASE
                        WHEN :enabled AND sync_tier = 'DISABLED' THEN 'COLD'
                        ELSE sync_tier
                    END,
                    next_sync_at = CASE
                        WHEN :enabled THEN CURRENT_TIMESTAMP
                        ELSE CURRENT_TIMESTAMP + INTERVAL '525600 minutes'
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE enabled IS DISTINCT FROM :enabled
                """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("enabled", enabled));
    }

    public void markSynced(long sectorId, SyncTier tier) {
        String sql = """
                UPDATE sync_sector
                SET last_synced_at = CURRENT_TIMESTAMP,
                    next_sync_at = CURRENT_TIMESTAMP + (:minutes || ' minutes')::interval,
                    updated_at = CURRENT_TIMESTAMP
                WHERE sector_id = :sectorId
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sectorId", sectorId)
                .addValue("minutes", nextDelayMinutes(tier)));
    }

    private static String selectSql() {
        return """
                SELECT sector_id, sector_code, center_lat, center_lon, katec_x, katec_y, radius_m,
                       side_length_m, sector_source, ST_AsText(sector_shape) AS sector_shape_wkt,
                       sync_tier, enabled, priority_score, last_synced_at, next_sync_at, memo
                FROM sync_sector
                """;
    }

    private static int nextDelayMinutes(SyncTier tier) {
        return switch (tier) {
            case HOT -> 1440;
            case WARM -> 4320;
            case COLD -> 525600;
            case DISABLED -> 525600;
        };
    }

    private static int priorityForTier(SyncTier tier) {
        return switch (tier) {
            case HOT -> 1000;
            case WARM -> 600;
            case COLD -> 100;
            case DISABLED -> 0;
        };
    }

    private static SyncSector mapSector(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp lastSyncedAt = rs.getTimestamp("last_synced_at");
        Timestamp nextSyncAt = rs.getTimestamp("next_sync_at");
        LocalDateTime last = lastSyncedAt == null ? null : lastSyncedAt.toLocalDateTime();
        LocalDateTime next = nextSyncAt == null ? null : nextSyncAt.toLocalDateTime();
        return new SyncSector(
                rs.getLong("sector_id"),
                rs.getString("sector_code"),
                rs.getDouble("center_lat"),
                rs.getDouble("center_lon"),
                rs.getDouble("katec_x"),
                rs.getDouble("katec_y"),
                rs.getInt("radius_m"),
                rs.getInt("side_length_m"),
                rs.getString("sector_source"),
                rs.getString("sector_shape_wkt"),
                SyncTier.valueOf(rs.getString("sync_tier")),
                rs.getBoolean("enabled"),
                rs.getInt("priority_score"),
                last,
                next,
                rs.getString("memo")
        );
    }
}
