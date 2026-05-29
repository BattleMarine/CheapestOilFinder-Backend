package com.oilpricedbmanager.external.opinet;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "opinet")
public record OpinetProperties(
        String baseUrl,
        String apiKey,
        String apiKeyFile,
        boolean enabled
) {
    public boolean hasApiKey() {
        return resolvedApiKey() != null && !resolvedApiKey().isBlank();
    }

    public String resolvedApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        if (apiKeyFile == null || apiKeyFile.isBlank()) {
            return "";
        }
        Path path = Path.of(apiKeyFile);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Opinet API key file: " + apiKeyFile, exception);
        }
    }
}