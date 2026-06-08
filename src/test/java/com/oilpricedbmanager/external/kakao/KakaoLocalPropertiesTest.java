package com.oilpricedbmanager.external.kakao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalPropertiesTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvedApiKeyUsesFirstLineFromFile() throws Exception {
        Path keyFile = tempDir.resolve("kakao_local_api.txt");
        Files.writeString(keyFile, "rest-key-123\nsecret-key-456\n");

        KakaoLocalProperties properties = new KakaoLocalProperties(
                "https://dapi.kakao.com/v2/local",
                "",
                keyFile.toString(),
                true,
                10,
                1440
        );

        assertThat(properties.resolvedApiKey()).isEqualTo("rest-key-123");
        assertThat(properties.hasCredentials()).isTrue();
    }
}
