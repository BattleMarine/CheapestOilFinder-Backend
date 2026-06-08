package com.oilpricedbmanager.external.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
        String baseUrl,
        String apiKey,
        String apiKeyFile,
        boolean enabled,
        int autocompleteLimit,
        int cacheTtlMinutes
) {
    public boolean hasCredentials() {
        return !resolvedApiKey().isBlank();
    }

    public String resolvedApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        List<String> lines = readKeyFile();
        if (lines.isEmpty()) {
            return "";
        }
        return lines.get(0).trim();
    }

    private List<String> readKeyFile() {
        if (apiKeyFile == null || apiKeyFile.isBlank()) {
            return List.of();
        }

        Path path = Path.of(apiKeyFile);
        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            return Files.readAllLines(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Kakao Local API key file: " + apiKeyFile, exception);
        }
    }
}
