package com.oilpricedbmanager.external.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "naver.directions")
public record NaverDirectionsProperties(
        String baseUrl,
        String apiKeyId,
        String apiKey,
        String apiKeyFile,
        boolean enabled
) {
    public boolean hasCredentials() {
        return !resolvedApiKeyId().isBlank() && !resolvedApiKey().isBlank();
    }

    public String resolvedApiKeyId() {
        if (apiKeyId != null && !apiKeyId.isBlank()) {
            return apiKeyId.trim();
        }
        List<String> lines = readKeyFile();
        if (lines.isEmpty()) {
            return "";
        }
        return lines.get(0).trim();
    }

    public String resolvedApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        List<String> lines = readKeyFile();
        if (lines.size() < 2) {
            return "";
        }
        return lines.get(1).trim();
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
            throw new IllegalStateException("Failed to read Naver Directions API key file: " + apiKeyFile, exception);
        }
    }
}
