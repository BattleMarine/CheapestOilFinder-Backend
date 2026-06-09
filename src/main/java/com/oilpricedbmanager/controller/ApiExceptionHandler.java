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
            return defaultJsonMessage();
        }

        String normalized = detail.toLowerCase();
        if (normalized.contains("unexpected character") || normalized.contains("was expecting double-quote")) {
            return "요청 본문이 올바른 JSON이 아닙니다. 필드명은 큰따옴표로 감싸고, PowerShell에서는 curl.exe 또는 올바른 JSON 문자열을 사용해 주세요.";
        }

        if (normalized.contains("stationsearchsortorder") || normalized.contains("cannot deserialize value of type")) {
            return "sortOrder 값이 백엔드에서 지원하는 형식과 맞지 않습니다. DISTANCE_ASC, CHEAPEST_FUEL_ASC, ESTIMATED_TOTAL_COST_ASC 또는 호환 별칭 PRICE_ASC를 사용해 주세요.";
        }

        if (normalized.contains("placesearchmode") || normalized.contains("placesearchsortorder")) {
            return "목적지 검색 API의 searchMode는 AUTO, KEYWORD, ADDRESS만 사용할 수 있고 sortOrder는 ACCURACY 또는 DISTANCE만 사용할 수 있습니다.";
        }

        return defaultJsonMessage();
    }

    private String defaultJsonMessage() {
        return "요청 JSON을 읽을 수 없습니다. JSON 형식과 enum 값을 다시 확인해 주세요.";
    }

    private String extractDetail(HttpMessageNotReadableException exception) {
        Throwable rootCause = exception.getMostSpecificCause();
        if (rootCause != null && rootCause.getMessage() != null) {
            return rootCause.getMessage();
        }
        return exception.getMessage();
    }
}
