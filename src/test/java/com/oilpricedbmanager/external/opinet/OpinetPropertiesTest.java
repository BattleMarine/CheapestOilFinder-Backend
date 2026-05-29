package com.oilpricedbmanager.external.opinet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpinetPropertiesTest {
    @TempDir
    Path tempDir;

    @Test
    void environmentApiKeyTakesPriorityOverFile() throws Exception {
        Path keyFile = tempDir.resolve("opinet-api-key.txt");
        Files.writeString(keyFile, "FILE_KEY");

        OpinetProperties properties = new OpinetProperties("http://example.com", "ENV_KEY", keyFile.toString(), true);

        assertThat(properties.resolvedApiKey()).isEqualTo("ENV_KEY");
    }

    @Test
    void readsApiKeyFromFileWhenEnvironmentKeyIsBlank() throws Exception {
        Path keyFile = tempDir.resolve("opinet-api-key.txt");
        Files.writeString(keyFile, "FILE_KEY\n");

        OpinetProperties properties = new OpinetProperties("http://example.com", "", keyFile.toString(), true);

        assertThat(properties.resolvedApiKey()).isEqualTo("FILE_KEY");
        assertThat(properties.hasApiKey()).isTrue();
    }
}