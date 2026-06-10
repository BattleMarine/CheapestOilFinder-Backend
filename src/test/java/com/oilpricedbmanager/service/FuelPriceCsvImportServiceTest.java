package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.FuelPriceCsvRecord;
import com.oilpricedbmanager.dto.FuelPriceCsvImportResponse;
import com.oilpricedbmanager.repository.FuelPriceImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelPriceCsvImportServiceTest {
    @Mock
    private FuelPriceImportRepository fuelPriceImportRepository;

    private FuelPriceCsvImportService service;

    @BeforeEach
    void setUp() {
        service = new FuelPriceCsvImportService(fuelPriceImportRepository);
    }

    @Test
    void importCsvUpsertsOnlyExistingStationsAndTreatsZeroAsNull() {
        String csv = "\uACE0\uC720\uBC88\uD638,\uC9C0\uC5ED,\uC0C1\uD638,\uC8FC\uC18C,\uC0C1\uD45C,\uC140\uD504\uC5EC\uBD80,\uACE0\uAE09\uD718\uBC1C\uC720,\uD718\uBC1C\uC720,\uACBD\uC720,\uC2E4\uB0B4\uB4F1\uC720\n"
                + "A0001,Seoul,Station,Address,Brand,Self,0,1700,1600,0\n"
                + "A9999,Seoul,Missing,Address,Brand,Self,2100,1800,1700,0\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fuel_prices.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
        when(fuelPriceImportRepository.findExistingStationIds(anyCollection())).thenReturn(Set.of("A0001"));
        when(fuelPriceImportRepository.findExistingFuelIds(anyCollection())).thenReturn(Set.of());
        when(fuelPriceImportRepository.upsertFuelPrices(anyList())).thenReturn(1);

        FuelPriceCsvImportResponse response = service.importCsv(file);

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.parsedRows()).isEqualTo(2);
        assertThat(response.insertedRows()).isEqualTo(1);
        assertThat(response.updatedRows()).isZero();
        assertThat(response.skippedMissingStationRows()).isEqualTo(1);
        assertThat(response.zeroOrBlankPriceCells()).isEqualTo(1);
        assertThat(response.missingStationIds()).containsExactly("A9999");

        ArgumentCaptor<List<FuelPriceCsvRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(fuelPriceImportRepository).upsertFuelPrices(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        FuelPriceCsvRecord record = captor.getValue().getFirst();
        assertThat(record.uniId()).isEqualTo("A0001");
        assertThat(record.premiumGasolineWon()).isNull();
        assertThat(record.regularGasolineWon()).isEqualTo(1700);
        assertThat(record.dieselWon()).isEqualTo(1600);
    }
    @Test
    void importCsvAcceptsMs949EncodedFile() {
        String csv = "\uACE0\uC720\uBC88\uD638,\uACE0\uAE09\uD718\uBC1C\uC720,\uD718\uBC1C\uC720,\uACBD\uC720\n"
                + "A0001,2100,1700,1600\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fuel_prices_ms949.csv",
                "text/csv",
                csv.getBytes(Charset.forName("MS949"))
        );
        when(fuelPriceImportRepository.findExistingStationIds(anyCollection())).thenReturn(Set.of("A0001"));
        when(fuelPriceImportRepository.findExistingFuelIds(anyCollection())).thenReturn(Set.of("A0001"));
        when(fuelPriceImportRepository.upsertFuelPrices(anyList())).thenReturn(1);

        FuelPriceCsvImportResponse response = service.importCsv(file);

        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.updatedRows()).isEqualTo(1);
        assertThat(response.insertedRows()).isZero();
    }
}