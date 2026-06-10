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
    void autoSearchCallsAddressAndKeywordAndCombinesResults() {
        when(placeSearchRepository.findFreshCache(any(), any())).thenReturn(Optional.empty());
        when(kakaoLocalClient.searchAddress(eq("서울 강남구 테헤란로 152"), eq(1), eq(10))).thenReturn(
                new KakaoLocalSearchResult(10, 10, true, placeItems("ADDR", 10))
        );
        when(kakaoLocalClient.searchKeyword(eq("서울 강남구 테헤란로 152"), eq(1), eq(15), eq(PlaceSearchSortOrder.ACCURACY), any(), any(), any())).thenReturn(
                new KakaoLocalSearchResult(15, 15, true, placeItems("KEY", 15))
        );

        PlaceSearchResponse response = service.search(new PlaceSearchRequest(
                "서울 강남구 테헤란로 152",
                PlaceSearchMode.AUTO,
                PlaceSearchSortOrder.ACCURACY,
                null,
                null,
                37.5,
                127.0,
                5000
        ));

        assertThat(response.searchMode()).isEqualTo(PlaceSearchMode.AUTO);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(25);
        assertThat(response.totalCount()).isEqualTo(25);
        assertThat(response.pageableCount()).isEqualTo(25);
        assertThat(response.items()).hasSize(25);
        assertThat(response.items().get(0).placeId()).isEqualTo("ADDR-1");
        assertThat(response.items().get(10).placeId()).isEqualTo("KEY-1");
        verify(kakaoLocalClient).searchAddress("서울 강남구 테헤란로 152", 1, 10);
        verify(kakaoLocalClient).searchKeyword("서울 강남구 테헤란로 152", 1, 15, PlaceSearchSortOrder.ACCURACY, 37.5, 127.0, 5000);
        verify(placeSearchRepository).saveCache(any(), eq("AUTO"), eq("서울 강남구 테헤란로 152"), eq("서울 강남구 테헤란로 152"), eq(1), eq(25), eq("ACCURACY"), any(), any(), any(), any(), any(), eq("kakao"), eq(200), any(), any(), org.mockito.ArgumentMatchers.isNull());
        verify(placeSearchRepository).upsertAutocompleteEntriesFromSearchResults(eq("서울 강남구 테헤란로 152"), any());
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

    private List<PlaceSearchItem> placeItems(String prefix, int count) {
        var items = new java.util.ArrayList<PlaceSearchItem>();
        for (int i = 1; i <= count; i++) {
            items.add(new PlaceSearchItem(
                    prefix + "-" + i,
                    prefix + " 장소 " + i,
                    "서울 강남구 테헤란로 " + i,
                    "서울 강남구 역삼동 " + i,
                    "장소",
                    null,
                    null,
                    37.5 + (i * 0.0001),
                    127.0 + (i * 0.0001),
                    i
            ));
        }
        return items;
    }
}
