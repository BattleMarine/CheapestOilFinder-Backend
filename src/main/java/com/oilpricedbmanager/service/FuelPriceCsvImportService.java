package com.oilpricedbmanager.service;

import com.oilpricedbmanager.domain.FuelPriceCsvRecord;
import com.oilpricedbmanager.dto.FuelPriceCsvImportResponse;
import com.oilpricedbmanager.repository.FuelPriceImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FuelPriceCsvImportService {
    private static final int WARNING_LIMIT = 20;
    private static final int MISSING_ID_LIMIT = 50;
    private static final String HEADER_UNI_ID = "\uACE0\uC720\uBC88\uD638";
    private static final String HEADER_PREMIUM_GASOLINE = "\uACE0\uAE09\uD718\uBC1C\uC720";
    private static final String HEADER_REGULAR_GASOLINE = "\uD718\uBC1C\uC720";
    private static final String HEADER_DIESEL = "\uACBD\uC720";

    private final FuelPriceImportRepository fuelPriceImportRepository;

    public FuelPriceCsvImportService(FuelPriceImportRepository fuelPriceImportRepository) {
        this.fuelPriceImportRepository = fuelPriceImportRepository;
    }

    public FuelPriceCsvImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty.");
        }

        CsvParseResult parseResult = parse(file);
        Set<String> stationIds = fuelPriceImportRepository.findExistingStationIds(parseResult.recordsByUniId.keySet());
        Set<String> existingFuelIds = fuelPriceImportRepository.findExistingFuelIds(stationIds);

        List<FuelPriceCsvRecord> recordsToUpsert = new ArrayList<>();
        List<String> missingStationIds = new ArrayList<>();
        int skippedMissingStationRows = 0;
        for (FuelPriceCsvRecord record : parseResult.recordsByUniId.values()) {
            if (stationIds.contains(record.uniId())) {
                recordsToUpsert.add(record);
            } else {
                skippedMissingStationRows++;
                if (missingStationIds.size() < MISSING_ID_LIMIT) {
                    missingStationIds.add(record.uniId());
                }
            }
        }

        Set<String> upsertIds = new HashSet<>();
        for (FuelPriceCsvRecord record : recordsToUpsert) {
            upsertIds.add(record.uniId());
        }
        int insertedRows = 0;
        for (String uniId : upsertIds) {
            if (!existingFuelIds.contains(uniId)) {
                insertedRows++;
            }
        }

        int affectedRows = fuelPriceImportRepository.upsertFuelPrices(recordsToUpsert);
        return new FuelPriceCsvImportResponse(
                file.getOriginalFilename(),
                file.getSize(),
                LocalDateTime.now(),
                parseResult.totalRows,
                parseResult.recordsByUniId.size(),
                affectedRows - insertedRows,
                insertedRows,
                skippedMissingStationRows,
                parseResult.invalidRows,
                parseResult.zeroOrBlankPriceCells,
                List.copyOf(missingStationIds),
                List.copyOf(parseResult.warnings)
        );
    }

    private CsvParseResult parse(MultipartFile file) {
        String content;
        try {
            content = decodeCsv(file.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read CSV file.", exception);
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV header is empty.");
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(parseCsvLine(headerLine));
            int uniIdIndex = requiredIndex(headerIndex, HEADER_UNI_ID);
            int premiumIndex = requiredIndex(headerIndex, HEADER_PREMIUM_GASOLINE);
            int regularIndex = requiredIndex(headerIndex, HEADER_REGULAR_GASOLINE);
            int dieselIndex = requiredIndex(headerIndex, HEADER_DIESEL);

            CsvParseResult result = new CsvParseResult();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                result.totalRows++;
                List<String> columns = parseCsvLine(line);
                try {
                    String uniId = valueAt(columns, uniIdIndex).trim();
                    if (uniId.isBlank()) {
                        throw new IllegalArgumentException("uni_id is empty.");
                    }

                    ParsedPrice premium = parsePrice(valueAt(columns, premiumIndex));
                    ParsedPrice regular = parsePrice(valueAt(columns, regularIndex));
                    ParsedPrice diesel = parsePrice(valueAt(columns, dieselIndex));
                    result.zeroOrBlankPriceCells += premium.zeroOrBlankCount() + regular.zeroOrBlankCount() + diesel.zeroOrBlankCount();

                    FuelPriceCsvRecord previous = result.recordsByUniId.put(uniId, new FuelPriceCsvRecord(
                            uniId,
                            premium.value(),
                            regular.value(),
                            diesel.value()
                    ));
                    if (previous != null) {
                        result.addWarning(lineNumber + " row has duplicate uni_id " + uniId + "; last row wins.");
                    }
                } catch (IllegalArgumentException exception) {
                    result.invalidRows++;
                    result.addWarning(lineNumber + " row skipped: " + exception.getMessage());
                }
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read CSV file.", exception);
        }
    }

    private String decodeCsv(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            return Charset.forName("MS949").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = stripBom(headers.get(i).trim());
            index.put(header, i);
        }
        return index;
    }

    private int requiredIndex(Map<String, Integer> headerIndex, String name) {
        Integer index = headerIndex.get(name);
        if (index == null) {
            throw new IllegalArgumentException("Missing required CSV column: " + name);
        }
        return index;
    }

    private String stripBom(String value) {
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private String valueAt(List<String> columns, int index) {
        if (index >= columns.size()) {
            return "";
        }
        return columns.get(index);
    }

    private ParsedPrice parsePrice(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().replace(",", "");
        if (normalized.isBlank()) {
            return new ParsedPrice(null, 1);
        }
        int value;
        try {
            value = Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("price is not numeric: " + rawValue);
        }
        if (value < 0) {
            throw new IllegalArgumentException("price cannot be negative: " + rawValue);
        }
        if (value == 0) {
            return new ParsedPrice(null, 1);
        }
        return new ParsedPrice(value, 0);
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (ch == ',' && !inQuote) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    private record ParsedPrice(Integer value, int zeroOrBlankCount) {
    }

    private static final class CsvParseResult {
        private final Map<String, FuelPriceCsvRecord> recordsByUniId = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();
        private int totalRows;
        private int invalidRows;
        private int zeroOrBlankPriceCells;

        private void addWarning(String warning) {
            if (warnings.size() < WARNING_LIMIT) {
                warnings.add(warning);
            }
        }
    }
}