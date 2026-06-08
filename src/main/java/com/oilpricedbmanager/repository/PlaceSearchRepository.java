package com.oilpricedbmanager.repository;

import com.oilpricedbmanager.dto.PlaceAutocompleteItem;
import com.oilpricedbmanager.dto.PlaceSearchItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class PlaceSearchRepository {
    private static final String GAS_STATION_SOURCE_TYPE = "GAS_STATION";
    private static final String KAKAO_SEARCH_SOURCE_TYPE = "KAKAO_SEARCH";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlaceSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void refreshAutocompleteIndexFromGasStations() {
        jdbcTemplate.update(
                "DELETE FROM place_autocomplete_entry WHERE source_type = :sourceType",
                new MapSqlParameterSource("sourceType", GAS_STATION_SOURCE_TYPE)
        );

        List<MapSqlParameterSource> entries = jdbcTemplate.query(
                """
                        SELECT uni_id, os_nm, addr, lat, lon
                        FROM gas_station
                        WHERE os_nm IS NOT NULL OR addr IS NOT NULL
                        ORDER BY uni_id
                        """,
                rs -> {
                    List<MapSqlParameterSource> params = new ArrayList<>();
                    while (rs.next()) {
                        params.addAll(buildGasStationEntries(rs));
                    }
                    return params;
                }
        );

        if (!entries.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    """
                            INSERT INTO place_autocomplete_entry (
                                source_type, source_ref, entry_type, display_text, normalized_text,
                                primary_text, secondary_text, latitude, longitude, search_weight, enabled, updated_at
                            ) VALUES (
                                :sourceType, :sourceRef, :entryType, :displayText, :normalizedText,
                                :primaryText, :secondaryText, :latitude, :longitude, :searchWeight, :enabled, :updatedAt
                            )
                            ON CONFLICT (source_type, source_ref, entry_type) DO UPDATE SET
                                display_text = EXCLUDED.display_text,
                                normalized_text = EXCLUDED.normalized_text,
                                primary_text = EXCLUDED.primary_text,
                                secondary_text = EXCLUDED.secondary_text,
                                latitude = EXCLUDED.latitude,
                                longitude = EXCLUDED.longitude,
                                search_weight = EXCLUDED.search_weight,
                                enabled = EXCLUDED.enabled,
                                updated_at = EXCLUDED.updated_at
                            """,
                    entries.toArray(MapSqlParameterSource[]::new)
            );
        }
    }

    public void upsertAutocompleteEntriesFromSearchResults(String query, List<PlaceSearchItem> items) {
        List<MapSqlParameterSource> entries = new ArrayList<>();
        for (PlaceSearchItem item : items) {
            entries.addAll(buildKakaoEntries(query, item));
        }

        if (entries.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO place_autocomplete_entry (
                            source_type, source_ref, entry_type, display_text, normalized_text,
                            primary_text, secondary_text, latitude, longitude, search_weight, enabled, updated_at
                        ) VALUES (
                            :sourceType, :sourceRef, :entryType, :displayText, :normalizedText,
                            :primaryText, :secondaryText, :latitude, :longitude, :searchWeight, :enabled, :updatedAt
                        )
                        ON CONFLICT (source_type, source_ref, entry_type) DO UPDATE SET
                            display_text = EXCLUDED.display_text,
                            normalized_text = EXCLUDED.normalized_text,
                            primary_text = EXCLUDED.primary_text,
                            secondary_text = EXCLUDED.secondary_text,
                            latitude = EXCLUDED.latitude,
                            longitude = EXCLUDED.longitude,
                            search_weight = EXCLUDED.search_weight,
                            enabled = EXCLUDED.enabled,
                            updated_at = EXCLUDED.updated_at
                        """,
                entries.toArray(MapSqlParameterSource[]::new)
        );
    }

    public List<PlaceAutocompleteItem> searchAutocomplete(String normalizedQuery, int limit) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return List.of();
        }

        String prefix = normalizedQuery + "%";
        return jdbcTemplate.query(
                """
                        SELECT entry_type, display_text, primary_text, secondary_text, source_type, source_ref, latitude, longitude
                        FROM place_autocomplete_entry
                        WHERE enabled = true
                          AND (normalized_text LIKE :prefix OR normalized_text LIKE '%' || :query || '%')
                        ORDER BY search_weight DESC,
                                 CASE WHEN normalized_text LIKE :prefix THEN 0 ELSE 1 END,
                                 similarity(normalized_text, :query) DESC,
                                 LENGTH(display_text) ASC,
                                 display_text ASC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("prefix", prefix)
                        .addValue("query", normalizedQuery)
                        .addValue("limit", limit),
                (rs, rowNum) -> new PlaceAutocompleteItem(
                        rs.getString("entry_type"),
                        rs.getString("display_text"),
                        rs.getString("primary_text"),
                        rs.getString("secondary_text"),
                        rs.getString("source_type"),
                        rs.getString("source_ref"),
                        getNullableDouble(rs, "latitude"),
                        getNullableDouble(rs, "longitude")
                )
        );
    }

    public Optional<String> findFreshCache(String cacheKey, LocalDateTime now) {
        List<String> result = jdbcTemplate.query(
                """
                        SELECT response_json
                        FROM place_search_cache
                        WHERE cache_key = :cacheKey
                          AND expires_at > :now
                        """,
                new MapSqlParameterSource()
                        .addValue("cacheKey", cacheKey)
                        .addValue("now", now),
                (rs, rowNum) -> rs.getString("response_json")
        );
        return result.stream().findFirst();
    }

    public void touchCache(String cacheKey, LocalDateTime now) {
        jdbcTemplate.update(
                """
                        UPDATE place_search_cache
                        SET last_hit_at = :now,
                            hit_count = hit_count + 1
                        WHERE cache_key = :cacheKey
                        """,
                new MapSqlParameterSource()
                        .addValue("cacheKey", cacheKey)
                        .addValue("now", now)
        );
    }

    public void saveCache(
            String cacheKey,
            String searchMode,
            String query,
            String normalizedQuery,
            int pageNo,
            int sizeNo,
            String sortOrder,
            Double centerLatitude,
            Double centerLongitude,
            Integer radiusMeters,
            String requestJson,
            String responseJson,
            String provider,
            int statusCode,
            LocalDateTime fetchedAt,
            LocalDateTime expiresAt,
            String errorMessage
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO place_search_cache (
                            cache_key, search_mode, query, normalized_query, page_no, size_no, sort_order,
                            center_latitude, center_longitude, radius_meters, request_json, response_json,
                            provider, status_code, fetched_at, expires_at, last_hit_at, hit_count, error_message
                        ) VALUES (
                            :cacheKey, :searchMode, :query, :normalizedQuery, :pageNo, :sizeNo, :sortOrder,
                            :centerLatitude, :centerLongitude, :radiusMeters, :requestJson, :responseJson,
                            :provider, :statusCode, :fetchedAt, :expiresAt, :lastHitAt, :hitCount, :errorMessage
                        )
                        ON CONFLICT (cache_key) DO UPDATE SET
                            search_mode = EXCLUDED.search_mode,
                            query = EXCLUDED.query,
                            normalized_query = EXCLUDED.normalized_query,
                            page_no = EXCLUDED.page_no,
                            size_no = EXCLUDED.size_no,
                            sort_order = EXCLUDED.sort_order,
                            center_latitude = EXCLUDED.center_latitude,
                            center_longitude = EXCLUDED.center_longitude,
                            radius_meters = EXCLUDED.radius_meters,
                            request_json = EXCLUDED.request_json,
                            response_json = EXCLUDED.response_json,
                            provider = EXCLUDED.provider,
                            status_code = EXCLUDED.status_code,
                            fetched_at = EXCLUDED.fetched_at,
                            expires_at = EXCLUDED.expires_at,
                            last_hit_at = place_search_cache.last_hit_at,
                            hit_count = place_search_cache.hit_count,
                            error_message = EXCLUDED.error_message
                        """,
                new MapSqlParameterSource()
                        .addValue("cacheKey", cacheKey)
                        .addValue("searchMode", searchMode)
                        .addValue("query", query)
                        .addValue("normalizedQuery", normalizedQuery)
                        .addValue("pageNo", pageNo)
                        .addValue("sizeNo", sizeNo)
                        .addValue("sortOrder", sortOrder)
                        .addValue("centerLatitude", centerLatitude)
                        .addValue("centerLongitude", centerLongitude)
                        .addValue("radiusMeters", radiusMeters)
                        .addValue("requestJson", requestJson)
                        .addValue("responseJson", responseJson)
                        .addValue("provider", provider)
                        .addValue("statusCode", statusCode)
                        .addValue("fetchedAt", fetchedAt)
                        .addValue("expiresAt", expiresAt)
                        .addValue("lastHitAt", fetchedAt)
                        .addValue("hitCount", 0)
                        .addValue("errorMessage", errorMessage)
        );
    }

    private List<MapSqlParameterSource> buildGasStationEntries(ResultSet rs) throws SQLException {
        String sourceRef = rs.getString("uni_id");
        String name = trimToNull(rs.getString("os_nm"));
        String address = trimToNull(rs.getString("addr"));
        Double latitude = getNullableDouble(rs, "lat");
        Double longitude = getNullableDouble(rs, "lon");
        List<MapSqlParameterSource> entries = new ArrayList<>();

        if (name != null) {
            entries.add(createEntry(GAS_STATION_SOURCE_TYPE, sourceRef, "NAME", name, name, address, latitude, longitude, 120));
        }
        if (address != null) {
            entries.add(createEntry(GAS_STATION_SOURCE_TYPE, sourceRef, "ADDRESS", address, address, name, latitude, longitude, 100));
        }
        if (name != null && address != null) {
            entries.add(createEntry(GAS_STATION_SOURCE_TYPE, sourceRef, "NAME_ADDRESS", name + " " + address, name, address, latitude, longitude, 110));
        }
        return entries;
    }

    private List<MapSqlParameterSource> buildKakaoEntries(String query, PlaceSearchItem item) {
        String sourceRef = item.placeId() == null || item.placeId().isBlank()
                ? hashSourceRef(query + "|" + nullToEmpty(item.placeName()) + "|" + nullToEmpty(item.addressName()))
                : item.placeId();
        List<MapSqlParameterSource> entries = new ArrayList<>();

        if (item.placeName() != null && !item.placeName().isBlank()) {
            entries.add(createEntry(KAKAO_SEARCH_SOURCE_TYPE, sourceRef, "PLACE_NAME", item.placeName(), item.placeName(), item.roadAddressName(), item.latitude(), item.longitude(), 150));
        }
        String roadAddress = firstNonBlank(item.roadAddressName(), item.addressName());
        if (roadAddress != null) {
            entries.add(createEntry(KAKAO_SEARCH_SOURCE_TYPE, sourceRef, "ROAD_ADDRESS", roadAddress, roadAddress, item.placeName(), item.latitude(), item.longitude(), 120));
        }
        return entries;
    }

    private MapSqlParameterSource createEntry(
            String sourceType,
            String sourceRef,
            String entryType,
            String displayText,
            String primaryText,
            String secondaryText,
            Double latitude,
            Double longitude,
            int searchWeight
    ) {
        String normalizedText = normalizeText(displayText);
        return new MapSqlParameterSource()
                .addValue("sourceType", sourceType)
                .addValue("sourceRef", sourceRef)
                .addValue("entryType", entryType)
                .addValue("displayText", displayText)
                .addValue("normalizedText", normalizedText)
                .addValue("primaryText", primaryText)
                .addValue("secondaryText", secondaryText)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("searchWeight", searchWeight)
                .addValue("enabled", true)
                .addValue("updatedAt", LocalDateTime.now());
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.KOREA)
                .replaceAll("[\\s\\p{Punct}]+", "")
                .trim();
    }

    private String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String hashSourceRef(String text) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build autocomplete source ref.", exception);
        }
    }
}
