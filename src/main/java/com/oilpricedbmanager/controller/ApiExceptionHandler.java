package com.oilpricedbmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        String detail = extractDetail(exception);
        String message = buildMessage(detail);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("path", request.getRequestURI());
        body.put("message", message);
        if (detail != null && !detail.isBlank()) {
            body.put("detail", detail);
        }

        return ResponseEntity.badRequest().body(body);
    }

    private String buildMessage(String detail) {
        if (detail == null || detail.isBlank()) {
            return "?붿껌 JSON???쎌쓣 ???놁뒿?덈떎. JSON ?뺤떇怨?enum 媛믪쓣 ?ㅼ떆 ?뺤씤??二쇱꽭??";
        }

        String normalized = detail.toLowerCase();
        if (normalized.contains("unexpected character") || normalized.contains("was expecting double-quote")) {
            return "?붿껌 蹂몃Ц???щ컮瑜?JSON???꾨떃?덈떎. ?꾨뱶紐낆? ?곕뵲?댄몴濡?媛먯떥怨? PowerShell?먯꽌??curl.exe ?먮뒗 ?щ컮瑜?JSON 臾몄옄?댁쓣 ?ъ슜??二쇱꽭??";
        }

        if (normalized.contains("stationsearchsortorder") || normalized.contains("cannot deserialize value of type")) {
            return "sortOrder 媛믪씠 諛깆뿏?쒖? 留욎? ?딆뒿?덈떎. DISTANCE_ASC, CHEAPEST_FUEL_ASC, ESTIMATED_TOTAL_COST_ASC ?먮뒗 ?명솚 蹂꾩묶 PRICE_ASC瑜??ъ슜??二쇱꽭??";
        }        if (normalized.contains("placesearchmode") || normalized.contains("placesearchsortorder")) {
            return "目的地 검색 API의 searchMode는 AUTO, KEYWORD, ADDRESS만 사용할 수 있고 sortOrder는 ACCURACY 또는 DISTANCE만 사용할 수 있습니다.";
        }


        return "?붿껌 JSON???쎌쓣 ???놁뒿?덈떎. JSON ?뺤떇怨?enum 媛믪쓣 ?ㅼ떆 ?뺤씤??二쇱꽭??";
    }

    private String extractDetail(HttpMessageNotReadableException exception) {
        Throwable rootCause = exception.getMostSpecificCause();
        if (rootCause != null && rootCause.getMessage() != null) {
            return rootCause.getMessage();
        }
        return exception.getMessage();
    }
}

