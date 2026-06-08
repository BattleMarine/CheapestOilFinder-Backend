package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.dto.PlaceAutocompleteResponse;
import com.oilpricedbmanager.dto.PlaceSearchRequest;
import com.oilpricedbmanager.dto.PlaceSearchResponse;
import com.oilpricedbmanager.service.PlaceSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/places")
public class PlaceSearchController {
    private final PlaceSearchService placeSearchService;

    public PlaceSearchController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    @GetMapping("/autocomplete")
    public PlaceAutocompleteResponse autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) Integer limit
    ) {
        return placeSearchService.autocomplete(query, limit);
    }

    @PostMapping("/search")
    public PlaceSearchResponse search(@Valid @RequestBody PlaceSearchRequest request) {
        return placeSearchService.search(request);
    }
}
