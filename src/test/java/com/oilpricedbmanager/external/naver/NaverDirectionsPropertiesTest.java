package com.oilpricedbmanager.external.naver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NaverDirectionsPropertiesTest {
    @TempDir
    Path tempDir;

    @Test
    void environmentValues_take_priority_over_file_values() throws Exception {
        Path keyFile = tempDir.resolve("naver-maps-client.txt");
        Files.writeString(keyFile, "FILE_ID\nFILE_KEY\n");

        NaverDirectionsProperties properties = new NaverDirectionsProperties(
                "https://example.com",
                "ENV_ID",
                "ENV_KEY",
                keyFile.toString(),
                true
        );

        assertThat(properties.resolvedApiKeyId()).isEqualTo("ENV_ID");
        assertThat(properties.resolvedApiKey()).isEqualTo("ENV_KEY");
    }

    @Test
    void reads_two_line_key_file_when_environment_values_are_blank() throws Exception {
        Path keyFile = tempDir.resolve("naver-maps-client.txt");
        Files.writeString(keyFile, "FILE_ID\nFILE_KEY\n");

        NaverDirectionsProperties properties = new NaverDirectionsProperties(
                "https://example.com",
                "",
                "",
                keyFile.toString(),
                true
        );

        assertThat(properties.resolvedApiKeyId()).isEqualTo("FILE_ID");
        assertThat(properties.resolvedApiKey()).isEqualTo("FILE_KEY");
        assertThat(properties.hasCredentials()).isTrue();
    }
}
