package com.oilpricedbmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oilpricedbmanager.dto.PlaceAutocompleteItem;
import com.oilpricedbmanager.dto.PlaceAutocompleteResponse;
import com.oilpricedbmanager.dto.PlaceSearchItem;
import com.oilpricedbmanager.dto.PlaceSearchMode;
import com.oilpricedbmanager.dto.PlaceSearchRequest;
import com.oilpricedbmanager.dto.PlaceSearchResponse;
import com.oilpricedbmanager.dto.PlaceSearchSortOrder;
import com.oilpricedbmanager.external.kakao.KakaoLocalClient;
import com.oilpricedbmanager.external.kakao.KakaoLocalProperties;
import com.oilpricedbmanager.external.kakao.KakaoLocalSearchResult;
import com.oilpricedbmanager.repository.PlaceSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class PlaceSearchService {
    private static final Logger log = LoggerFactory.getLogger(PlaceSearchService.class);

    private final KakaoLocalProperties properties;
    private final KakaoLocalClient kakaoLocalClient;
    private final PlaceSearchRepository placeSearchRepository;
    private final ObjectMapper objectMapper;

    public PlaceSearchService(
            KakaoLocalProperties properties,
            KakaoLocalClient kakaoLocalClient,
            PlaceSearchRepository placeSearchRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.kakaoLocalClient = kakaoLocalClient;
        this.placeSearchRepository = placeSearchRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeAutocompleteIndex() {
        try {
            placeSearchRepository.refreshAutocompleteIndexFromGasStations();
        } catch (RuntimeException exception) {
            log.warn("Failed to initialize place autocomplete index.", exception);
        }
    }

    public PlaceAutocompleteResponse autocomplete(String query, Integer limit) {
        String resolvedQuery = normalize(query);
        if (resolvedQuery.isBlank()) {
            return new PlaceAutocompleteResponse(query, 0, List.of());
        }

        int resolvedLimit = limit == null || limit < 1 ? properties.autocompleteLimit() : Math.min(limit, 20);
        List<PlaceAutocompleteItem> items = placeSearchRepository.searchAutocomplete(resolvedQuery, resolvedLimit);
        return new PlaceAutocompleteResponse(query, items.size(), items);
    }

    public PlaceSearchResponse search(PlaceSearchRequest request) {
        String rawQuery = request.resolvedQuery();
        if (rawQuery.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }

        String normalizedQuery = normalize(rawQuery);
        PlaceSearchMode resolvedMode = resolveMode(request.resolvedSearchMode(), rawQuery);
        PlaceSearchSortOrder resolvedSortOrder = request.resolvedSortOrder();
        if (resolvedSortOrder == PlaceSearchSortOrder.DISTANCE
                && (request.centerLatitude() == null || request.centerLongitude() == null)) {
            resolvedSortOrder = PlaceSearchSortOrder.ACCURACY;
        }

        int page = request.resolvedPage();
        int size = request.resolvedSize();
        Integer radiusMeters = request.resolvedRadiusMeters();
        LocalDateTime now = LocalDateTime.now();
        String cacheKey = buildCacheKey(resolvedMode, normalizedQuery, resolvedSortOrder, page, size, request.centerLatitude(), request.centerLongitude(), radiusMeters);

        String cachedJson = placeSearchRepository.findFreshCache(cacheKey, now).orElse(null);
        if (cachedJson != null) {
            placeSearchRepository.touchCache(cacheKey, now);
            return withCached(readResponse(cachedJson), true);
        }

        KakaoLocalSearchResult kakaoResult = searchKakao(request, resolvedMode, resolvedSortOrder, page, size, radiusMeters, rawQuery);
        PlaceSearchResponse response = new PlaceSearchResponse(
                resolvedMode,
                resolvedSortOrder,
                rawQuery,
                page,
                size,
                kakaoResult.totalCount(),
                kakaoResult.pageableCount(),
                kakaoResult.end(),
                false,
                kakaoResult.items()
        );

        placeSearchRepository.saveCache(
                cacheKey,
                resolvedMode.name(),
                rawQuery,
                normalizedQuery,
                page,
                size,
                resolvedSortOrder.name(),
                request.centerLatitude(),
                request.centerLongitude(),
                radiusMeters,
                writeJson(request),
                writeJson(response),
                "kakao",
                200,
                now,
                now.plusMinutes(properties.cacheTtlMinutes()),
                null
        );
        placeSearchRepository.upsertAutocompleteEntriesFromSearchResults(rawQuery, kakaoResult.items());
        return response;
    }

    private KakaoLocalSearchResult searchKakao(
            PlaceSearchRequest request,
            PlaceSearchMode resolvedMode,
            PlaceSearchSortOrder resolvedSortOrder,
            int page,
            int size,
            Integer radiusMeters,
            String rawQuery
    ) {
        return switch (resolvedMode) {
            case ADDRESS -> kakaoLocalClient.searchAddress(rawQuery, page, size);
            case KEYWORD -> kakaoLocalClient.searchKeyword(
                    rawQuery,
                    page,
                    size,
                    resolvedSortOrder,
                    request.centerLatitude(),
                    request.centerLongitude(),
                    radiusMeters
            );
            case AUTO -> throw new IllegalStateException("AUTO mode should be resolved before Kakao search.");
        };
    }

    private PlaceSearchMode resolveMode(PlaceSearchMode requestedMode, String query) {
        if (requestedMode == null || requestedMode == PlaceSearchMode.AUTO) {
            return looksLikeAddress(query) ? PlaceSearchMode.ADDRESS : PlaceSearchMode.KEYWORD;
        }
        return requestedMode;
    }

    private boolean looksLikeAddress(String query) {
        String normalized = query.replaceAll("\\s+", "");
        return normalized.matches(".*(로|길|대로|번길|동|읍|면|리|구|시|군|번지).*")
                || normalized.matches(".*\\d+.*");
    }

    private PlaceSearchResponse readResponse(String json) {
        try {
            return objectMapper.readValue(json, PlaceSearchResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read cached place search response.", exception);
        }
    }

    private PlaceSearchResponse withCached(PlaceSearchResponse response, boolean cached) {
        return new PlaceSearchResponse(
                response.searchMode(),
                response.sortOrder(),
                response.query(),
                response.page(),
                response.size(),
                response.totalCount(),
                response.pageableCount(),
                response.end(),
                cached,
                response.items()
        );
    }

    private String buildCacheKey(
            PlaceSearchMode mode,
            String normalizedQuery,
            PlaceSearchSortOrder sortOrder,
            int page,
            int size,
            Double centerLatitude,
            Double centerLongitude,
            Integer radiusMeters
    ) {
        String canonical = String.join("|",
                "kakao-local-search",
                mode.name(),
                normalizedQuery,
                sortOrder.name(),
                Integer.toString(page),
                Integer.toString(size),
                canonicalCoordinate(centerLatitude),
                canonicalCoordinate(centerLongitude),
                radiusMeters == null ? "" : radiusMeters.toString()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build Kakao Local cache key.", exception);
        }
    }

    private String canonicalCoordinate(Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.US, "%.4f", value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize Kakao Local payload.", exception);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
