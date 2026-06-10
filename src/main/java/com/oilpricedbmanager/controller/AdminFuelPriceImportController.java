package com.oilpricedbmanager.controller;

import com.oilpricedbmanager.dto.FuelPriceCsvImportResponse;
import com.oilpricedbmanager.service.FuelPriceCsvImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/import")
public class AdminFuelPriceImportController {
    private final FuelPriceCsvImportService fuelPriceCsvImportService;

    public AdminFuelPriceImportController(FuelPriceCsvImportService fuelPriceCsvImportService) {
        this.fuelPriceCsvImportService = fuelPriceCsvImportService;
    }

    @PostMapping(value = "/fuel-prices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importFuelPrices(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        try {
            FuelPriceCsvImportResponse response = fuelPriceCsvImportService.importCsv(file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException exception) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", OffsetDateTime.now().toString());
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
            body.put("path", request.getRequestURI());
            body.put("message", exception.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }
}