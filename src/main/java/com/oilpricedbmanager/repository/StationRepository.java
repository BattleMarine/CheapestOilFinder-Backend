package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.domain.FuelType;
import com.oilpricedbmanager.domain.StationFuelCandidate;
import com.oilpricedbmanager.domain.StationFuelSnapshot;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class StationRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public StationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StationFuelCandidate> findNearby(double lat, double lon, int radiusMeters, FuelType fuelType) {
        String priceExpression = switch (fuelType) {
            case REGULAR_GASOLINE -> "f.gas_low";
            case PREMIUM_GASOLINE -> "f.gas_hign";
            case DIESEL -> "f.disl";
            case LPG -> "f.lpg";
        };

        String sql = """
                SELECT
                    gs.uni_id,
                    gs.poll_div_cd,
                    gs.os_nm,
                    gs.phone,
                    gs.addr,
                    gs.lat,
                    gs.lon,
                    %s AS price_per_liter,
                    ROUND(ST_Distance(
                        gs.geom::geography,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                    ))::int AS distance_meters,
                    f.updated_at AS fuel_updated_at
                FROM gas_station gs
                JOIN fuel f ON gs.uni_id = f.uni_id
                WHERE %s IS NOT NULL
                  AND ST_DWithin(
                        gs.geom::geography,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                        :radiusMeters
                  )
                ORDER BY distance_meters ASC
                LIMIT 200
                """.formatted(priceExpression, priceExpression);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lon", lon)
                .addValue("radiusMeters", radiusMeters);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new StationFuelCandidate(
                rs.getString("uni_id"),
                rs.getString("poll_div_cd"),
                rs.getString("os_nm"),
                rs.getString("phone"),
                rs.getString("addr"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                fuelType,
                rs.getInt("price_per_liter"),
                rs.getInt("distance_meters"),
                rs.getObject("fuel_updated_at", Timestamp.class).toLocalDateTime()
        ));
    }

    public List<StationFuelSnapshot> findNearbySnapshots(double lat, double lon, int radiusMeters, String routeWkt, List<FuelType> fuelTypes) {
        boolean routeSearch = routeWkt != null && !routeWkt.isBlank();
        String distanceExpression = routeSearch
                ? "ROUND(ST_Distance(gs.geom::geography, ST_SetSRID(ST_GeomFromText(:routeWkt), 4326)::geography))::int"
                : "ROUND(ST_Distance(gs.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography))::int";
        String withinExpression = routeSearch
                ? "ST_DWithin(gs.geom::geography, ST_SetSRID(ST_GeomFromText(:routeWkt), 4326)::geography, :radiusMeters)"
                : "ST_DWithin(gs.geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radiusMeters)";
        String fuelAvailabilityCondition = fuelAvailabilityCondition(fuelTypes);

        String sql = """
                SELECT
                    gs.uni_id,
                    gs.poll_div_cd,
                    gs.os_nm,
                    gs.phone,
                    gs.addr,
                    gs.lat,
                    gs.lon,
                    f.gas_hign,
                    f.gas_low,
                    f.disl,
                    f.lpg,
                    %s AS distance_meters,
                    f.updated_at AS fuel_updated_at
                FROM gas_station gs
                JOIN fuel f ON gs.uni_id = f.uni_id
                WHERE (%s)
                  AND (%s)
                ORDER BY distance_meters ASC, gs.uni_id ASC
                """.formatted(distanceExpression, withinExpression, fuelAvailabilityCondition);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lat", lat)
                .addValue("lon", lon)
                .addValue("radiusMeters", radiusMeters)
                .addValue("routeWkt", routeWkt);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new StationFuelSnapshot(
                rs.getString("uni_id"),
                rs.getString("poll_div_cd"),
                rs.getString("os_nm"),
                rs.getString("phone"),
                rs.getString("addr"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getObject("gas_hign", Integer.class),
                rs.getObject("gas_low", Integer.class),
                rs.getObject("disl", Integer.class),
                rs.getObject("lpg", Integer.class),
                rs.getInt("distance_meters"),
                rs.getObject("fuel_updated_at", Timestamp.class).toLocalDateTime()
        ));
    }

    private String fuelAvailabilityCondition(List<FuelType> fuelTypes) {
        var conditions = new ArrayList<String>();
        List<FuelType> effectiveFuelTypes = fuelTypes == null || fuelTypes.isEmpty()
                ? List.of(FuelType.REGULAR_GASOLINE, FuelType.PREMIUM_GASOLINE, FuelType.DIESEL, FuelType.LPG)
                : fuelTypes;

        for (FuelType fuelType : effectiveFuelTypes) {
            String condition = switch (fuelType) {
                case REGULAR_GASOLINE -> "f.gas_low IS NOT NULL";
                case PREMIUM_GASOLINE -> "f.gas_hign IS NOT NULL";
                case DIESEL -> "f.disl IS NOT NULL";
                case LPG -> "f.lpg IS NOT NULL";
            };
            if (!conditions.contains(condition)) {
                conditions.add(condition);
            }
        }
        return String.join(" OR ", conditions);
    }

    public Optional<StationFuelSnapshot> findSnapshotById(String stationId) {
        String sql = """
                SELECT
                    gs.uni_id,
                    gs.poll_div_cd,
                    gs.os_nm,
                    gs.phone,
                    gs.addr,
                    gs.lat,
                    gs.lon,
                    f.gas_hign,
                    f.gas_low,
                    f.disl,
                    f.lpg,
                    0 AS distance_meters,
                    f.updated_at AS fuel_updated_at
                FROM gas_station gs
                LEFT JOIN fuel f ON gs.uni_id = f.uni_id
                WHERE gs.uni_id = :stationId
                """;

        List<StationFuelSnapshot> result = jdbcTemplate.query(sql, new MapSqlParameterSource()
                        .addValue("stationId", stationId),
                (rs, rowNum) -> new StationFuelSnapshot(
                        rs.getString("uni_id"),
                        rs.getString("poll_div_cd"),
                        rs.getString("os_nm"),
                        rs.getString("phone"),
                        rs.getString("addr"),
                        rs.getDouble("lat"),
                        rs.getDouble("lon"),
                        rs.getObject("gas_hign", Integer.class),
                        rs.getObject("gas_low", Integer.class),
                        rs.getObject("disl", Integer.class),
                        rs.getObject("lpg", Integer.class),
                        rs.getInt("distance_meters"),
                        rs.getObject("fuel_updated_at", Timestamp.class) == null
                                ? null
                                : rs.getObject("fuel_updated_at", Timestamp.class).toLocalDateTime()
                ));
        return result.stream().findFirst();
    }
}
