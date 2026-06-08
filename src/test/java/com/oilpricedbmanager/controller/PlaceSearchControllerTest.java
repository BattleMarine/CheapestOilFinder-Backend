package com.oilpricedbmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oilpricedbmanager.dto.PlaceAutocompleteResponse;
import com.oilpricedbmanager.dto.PlaceSearchMode;
import com.oilpricedbmanager.dto.PlaceSearchRequest;
import com.oilpricedbmanager.dto.PlaceSearchResponse;
import com.oilpricedbmanager.dto.PlaceSearchSortOrder;
import com.oilpricedbmanager.service.PlaceSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceSearchController.class)
class PlaceSearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlaceSearchService placeSearchService;

    @Test
    void autocompleteDelegatesToService() throws Exception {
        when(placeSearchService.autocomplete("강남역", 10)).thenReturn(new PlaceAutocompleteResponse("강남역", 0, List.of()));

        mockMvc.perform(get("/api/places/autocomplete")
                        .param("query", "강남역"))
                .andExpect(status().isOk());

        verify(placeSearchService).autocomplete("강남역", 10);
    }

    @Test
    void searchDelegatesToService() throws Exception {
        PlaceSearchRequest request = new PlaceSearchRequest(
                "강남역",
                PlaceSearchMode.AUTO,
                PlaceSearchSortOrder.ACCURACY,
                1,
                10,
                37.5,
                127.0,
                5000
        );
        when(placeSearchService.search(any(PlaceSearchRequest.class))).thenReturn(new PlaceSearchResponse(
                PlaceSearchMode.KEYWORD,
                PlaceSearchSortOrder.ACCURACY,
                "강남역",
                1,
                10,
                0,
                0,
                true,
                false,
                List.of()
        ));

        mockMvc.perform(post("/api/places/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(placeSearchService).search(any(PlaceSearchRequest.class));
    }
}

