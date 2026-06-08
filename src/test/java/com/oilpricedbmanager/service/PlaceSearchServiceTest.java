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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {
    @Mock
    private PlaceSearchRepository placeSearchRepository;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlaceSearchService service;

    @BeforeEach
    void setUp() {
        service = new PlaceSearchService(
                new KakaoLocalProperties("https://dapi.kakao.com/v2/local", "dummy", null, true, 10, 1440),
                kakaoLocalClient,
                placeSearchRepository,
                objectMapper
        );
    }

    @Test
    void autocompleteUsesRepositoryOnly() {
        when(placeSearchRepository.searchAutocomplete(eq("강남역"), eq(5))).thenReturn(List.of(
                PlaceAutocompleteItem.of("NAME", "강남역", "강남역", "서울특별시 강남구", "GAS_STATION", "A0001", 37.5, 127.0)
        ));

        PlaceAutocompleteResponse response = service.autocomplete("강남역", 5);

        assertThat(response.resultCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        verify(placeSearchRepository).searchAutocomplete("강남역", 5);
        verify(kakaoLocalClient, never()).searchKeyword(any(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void searchUsesCacheWhenAvailable() throws Exception {
        PlaceSearchResponse cached = new PlaceSearchResponse(
                PlaceSearchMode.KEYWORD,
                PlaceSearchSortOrder.ACCURACY,
                "강남역",
                1,
                10,
                1,
                1,
                true,
                false,
                List.of(new PlaceSearchItem("1", "강남역", "서울 강남구", "서울 강남구", "지하철역", "02-1234-5678", "https://place.map", 37.5, 127.0, 10))
        );
        when(placeSearchRepository.findFreshCache(any(), any())).thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));

        PlaceSearchResponse response = service.search(new PlaceSearchRequest(
                "강남역",
                PlaceSearchMode.AUTO,
                PlaceSearchSortOrder.ACCURACY,
                1,
                10,
                37.5,
                127.0,
                5000
        ));

        assertThat(response.cached()).isTrue();
        assertThat(response.items()).hasSize(1);
        verify(placeSearchRepository).touchCache(any(), any());
        verify(kakaoLocalClient, never()).searchKeyword(any(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void searchCallsKakaoOnCacheMiss() {
        when(placeSearchRepository.findFreshCache(any(), any())).thenReturn(Optional.empty());
        when(kakaoLocalClient.searchKeyword(any(), anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(
                new KakaoLocalSearchResult(1, 1, true, List.of(
                        new PlaceSearchItem("1", "강남역", "서울 강남구", "서울 강남구", "지하철역", "02-1234-5678", "https://place.map", 37.5, 127.0, 10)
                ))
        );

        PlaceSearchResponse response = service.search(new PlaceSearchRequest(
                "강남역",
                PlaceSearchMode.KEYWORD,
                PlaceSearchSortOrder.DISTANCE,
                1,
                10,
                37.5,
                127.0,
                5000
        ));

        assertThat(response.cached()).isFalse();
        assertThat(response.totalCount()).isEqualTo(1);
        verify(placeSearchRepository).saveCache(any(), eq("KEYWORD"), eq("강남역"), eq("강남역"), eq(1), eq(10), eq("DISTANCE"), any(), any(), any(), any(), any(), eq("kakao"), eq(200), any(), any(), org.mockito.ArgumentMatchers.isNull());
        verify(placeSearchRepository).upsertAutocompleteEntriesFromSearchResults(eq("강남역"), any());
    }
}

